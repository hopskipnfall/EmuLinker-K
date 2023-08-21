package org.emulinker.kaillera.controller.v086

import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.network.sockets.Datagram
import io.ktor.utils.io.core.ByteReadPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.sync.Mutex
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.kaillera.controller.v086.action.*
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle.Companion.parse
import org.emulinker.kaillera.controller.v086.protocol.V086BundleFormatException
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.event.*
import org.emulinker.util.ClientGameDataCache
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.dumpBuffer
import org.emulinker.util.GameDataCache
import org.emulinker.util.stripFromProdBinary

/** A private UDP server allocated for communication with a single client. */
class V086ClientHandler
@AssistedInject
constructor(
  metrics: MetricRegistry,
  private val flags: RuntimeFlags,
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  @Assisted val connectRemoteSocketAddress: InetSocketAddress,
  @param:Assisted val controller: V086Controller,
  /** The [CombinedKailleraController] that created this instance. */
  @param:Assisted val combinedKailleraController: CombinedKailleraController,
) : KailleraEventListener {
  val mutex = Mutex()

  var remoteSocketAddress: InetSocketAddress? = null
    private set

  fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    if (this.remoteSocketAddress == null) {
      this.remoteSocketAddress = remoteSocketAddress
    } else if (remoteSocketAddress != this.remoteSocketAddress) {
      logger
        .atWarning()
        .log(
          "Rejecting packet received from wrong address: %s != %s",
          EmuUtil.formatSocketAddress(remoteSocketAddress),
          EmuUtil.formatSocketAddress(this.remoteSocketAddress!!)
        )
      return
    }
    clientRequestTimer.time().use { handleReceivedInternal(buffer) }
  }

  lateinit var user: KailleraUser
    private set

  private var messageNumberCounter = 0

  // TODO(nue): Add this to RuntimeFlags and increase to at least 5.
  val numAcksForSpeedTest = 3

  private var prevMessageNumber = -1
    private set
  private var lastMessageNumber = -1
    private set
  var clientGameDataCache: GameDataCache = ClientGameDataCache(256)
    private set
  var serverGameDataCache: GameDataCache = ClientGameDataCache(256)
    private set

  private val lastMessageBuffer = LastMessageBuffer(V086Controller.MAX_BUNDLE_SIZE)
  private val outMessages = arrayOfNulls<V086Message>(V086Controller.MAX_BUNDLE_SIZE)
  private var outBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)
  private val inSynch = Any()
  private val outSynch = Any()
  private var testStart: Long = 0
  private var lastMeasurement: Long = 0
  var speedMeasurementCount = 0
    private set
  var bestNetworkSpeed = Int.MAX_VALUE
    private set
  private var clientRetryCount = 0
  private var lastResend = 0L

  private val clientRequestTimer =
    metrics.timer(MetricRegistry.name(this.javaClass, "V086ClientRequests"))

  @AssistedFactory
  interface Factory {
    fun create(
      remoteSocketAddress: InetSocketAddress,
      v086Controller: V086Controller,
      combinedKailleraController: CombinedKailleraController,
    ): V086ClientHandler
  }

  @get:Synchronized
  val nextMessageNumber: Int
    get() {
      if (messageNumberCounter > 0xFFFF) messageNumberCounter = 0
      return messageNumberCounter++
    }

  fun resetGameDataCache() {
    clientGameDataCache = ClientGameDataCache(256)
    /*SF MOD - Button Ghosting Patch
    serverCache = new ServerGameDataCache(256);
    */
    serverGameDataCache = ClientGameDataCache(256)
  }

  fun startSpeedTest() {
    lastMeasurement = System.currentTimeMillis()
    testStart = lastMeasurement
    speedMeasurementCount = 0
  }

  fun addSpeedMeasurement() {
    val et = (System.currentTimeMillis() - lastMeasurement).toInt()
    if (et < bestNetworkSpeed) {
      bestNetworkSpeed = et
    }
    speedMeasurementCount++
    lastMeasurement = System.currentTimeMillis()
  }

  val averageNetworkSpeed: Int
    get() = ((lastMeasurement - testStart) / speedMeasurementCount).toInt()

  fun start(user: KailleraUser) {
    this.user = user
    controller.clientHandlers[user.id] = this
  }

  override fun stop() {
    controller.clientHandlers.remove(user.id)
    combinedKailleraController.clientHandlers.remove(remoteSocketAddress)
  }

  private fun handleReceivedInternal(buffer: ByteBuffer) {
    val inBundle =
      try {
        parse(buffer, lastMessageNumber)
      } catch (e: ParseException) {
        buffer.rewind()
        logger.atWarning().withCause(e).log("%s failed to parse: %s", this, dumpBuffer(buffer))
        null
      } catch (e: V086BundleFormatException) {
        buffer.rewind()
        logger
          .atWarning()
          .withCause(e)
          .log("%s received invalid message bundle: %s", this, dumpBuffer(buffer))
        null
      } catch (e: MessageFormatException) {
        buffer.rewind()
        logger
          .atWarning()
          .withCause(e)
          .log("%s received invalid message: %s}", this, dumpBuffer(buffer))
        null
      } ?: return

    stripFromProdBinary {
      logger.atFinest().log("<- FROM P%d: %s", user.id, inBundle.messages.firstOrNull())
    }
    clientRetryCount =
      if (inBundle.numMessages == 0) {
        logger
          .atFine()
          .log("%s received bundle of %d messages from %s", this, inBundle.numMessages, user)
        clientRetryCount++
        resend(clientRetryCount)
        return
      } else {
        0
      }
    try {
      synchronized(inSynch) {
        val messages = inBundle.messages
        if (inBundle.numMessages == 1) {
          lastMessageNumber = messages[0]!!.messageNumber
          val action = controller.actions[messages[0]!!.messageTypeId.toInt()]
          if (action == null) {
            logger
              .atSevere()
              .log("No action defined to handle client message: %s", messages.firstOrNull())
          }
          (action as V086Action<V086Message>).performAction(messages[0]!!, this)
        } else {
          // read the bundle from back to front to process the oldest messages first
          for (i in inBundle.numMessages - 1 downTo 0) {
            /**
             * already extracts messages with higher numbers when parsing, it does not need to be
             * checked and this causes an error if messageNumber is 0 and lastMessageNumber is
             * 0xFFFF if (messages [i].getNumber() > lastMessageNumber)
             */
            prevMessageNumber = lastMessageNumber
            lastMessageNumber = messages[i]!!.messageNumber
            if (prevMessageNumber + 1 != lastMessageNumber) {
              if (prevMessageNumber == 0xFFFF && lastMessageNumber == 0) {
                // exception; do nothing
              } else {
                logger
                  .atWarning()
                  .log(
                    "%s dropped a packet! (%d to %d)",
                    user,
                    prevMessageNumber,
                    lastMessageNumber
                  )
                user.droppedPacket()
              }
            }
            val action = controller.actions[messages[i]!!.messageTypeId.toInt()]
            if (action == null) {
              logger.atSevere().log("No action defined to handle client message: %s", messages[i])
            } else {
              // logger.atFine().log(user + " -> " + message);
              (action as V086Action<V086Message>).performAction(messages[i]!!, this)
            }
          }
        }
      }
    } catch (e: FatalActionException) {
      logger.atWarning().withCause(e).log("%s fatal action, closing connection", this)
      stop()
    }
  }

  override fun actionPerformed(event: KailleraEvent) {
    when (event) {
      is GameEvent -> {
        val eventHandler = controller.gameEventHandlers[event::class]
        if (eventHandler == null) {
          logger
            .atSevere()
            .log("%s found no GameEventHandler registered to handle game event: %s", this, event)
          return
        }
        (eventHandler as V086GameEventHandler<GameEvent>).handleEvent(event, this)
      }
      is ServerEvent -> {
        val eventHandler = controller.serverEventHandlers[event::class]
        if (eventHandler == null) {
          logger
            .atSevere()
            .log(
              "%s found no ServerEventHandler registered to handle server event: %s",
              this,
              event
            )
          return
        }
        (eventHandler as V086ServerEventHandler<ServerEvent>).handleEvent(event, this)
      }
      is UserEvent -> {
        val eventHandler = controller.userEventHandlers[event::class]
        if (eventHandler == null) {
          logger
            .atSevere()
            .log("%s found no UserEventHandler registered to handle user event: ", this, event)
          return
        }
        (eventHandler as V086UserEventHandler<UserEvent>).handleEvent(event, this)
      }
      is StopFlagEvent -> {}
    }
  }

  fun resend(timeoutCounter: Int) {
    synchronized(outSynch) {
      // if ((System.currentTimeMillis() - lastResend) > (user.getPing()*3))
      if (System.currentTimeMillis() - lastResend > controller.server.maxPing) {
        // int numToSend = (3+timeoutCounter);
        var numToSend = 3 * timeoutCounter
        if (numToSend > V086Controller.MAX_BUNDLE_SIZE) numToSend = V086Controller.MAX_BUNDLE_SIZE
        logger.atFine().log("%s: resending last %d messages", this, numToSend)
        send(null, numToSend)
        lastResend = System.currentTimeMillis()
      } else {
        logger.atFine().log("Skipping resend...")
      }
    }
  }

  fun send(outMessage: V086Message?, numToSend: Int = 5) {
    var numToSend = numToSend
    synchronized(outSynch) {
      if (outMessage != null) {
        lastMessageBuffer.add(outMessage)
      }
      numToSend = lastMessageBuffer.fill(outMessages, numToSend)
      val outBundle = V086Bundle(outMessages, numToSend)
      stripFromProdBinary { logger.atFinest().log("<- TO P%d: %s", user.id, outMessage) }
      outBundle.writeTo(outBuffer)
      outBuffer.flip()
      combinedKailleraController.outChannel
        .trySendBlocking(Datagram(ByteReadPacket(outBuffer), remoteSocketAddress!!.toKtorAddress()))
        .onSuccess {
          // TODO(nue): This is inefficient! Try to use a set number of buffers and rotate between
          // them. Or use 1 like the original code does. We will need to call `.clear()` on it
          // before using it again.
          outBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)
          outBuffer.order(ByteOrder.LITTLE_ENDIAN)
        }
        .onFailure { logger.atWarning().withCause(it).log("Failed to add to outBuffer: %s") }
    }
  }

  init {
    outBuffer.order(ByteOrder.LITTLE_ENDIAN)
    resetGameDataCache()
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
