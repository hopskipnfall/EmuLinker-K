package org.emulinker.kaillera.controller.v086

import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.netty.buffer.Unpooled
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.action.FatalActionException
import org.emulinker.kaillera.controller.v086.action.V086Action
import org.emulinker.kaillera.controller.v086.action.V086GameEventHandler
import org.emulinker.kaillera.controller.v086.action.V086ServerEventHandler
import org.emulinker.kaillera.controller.v086.action.V086UserEventHandler
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle.Companion.parse
import org.emulinker.kaillera.controller.v086.protocol.V086BundleFormatException
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.event.GameEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.KailleraEventListener
import org.emulinker.kaillera.model.event.ServerEvent
import org.emulinker.kaillera.model.event.StopFlagEvent
import org.emulinker.kaillera.model.event.UserEvent
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.util.ClientGameDataCache
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning
import org.emulinker.util.GameDataCache
import org.emulinker.util.stripFromProdBinary

class V086ClientHandler
@AssistedInject
constructor(
  metrics: MetricRegistry,
  flags: RuntimeFlags,
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  @Assisted val connectRemoteSocketAddress: InetSocketAddress,
  @param:Assisted val controller: V086Controller,
  /** The [CombinedKailleraController] that created this instance. */
  @param:Assisted val combinedKailleraController: CombinedKailleraController,
) : KailleraEventListener {
  /** Mutex ensuring that only one packet is processed at a time for this [V086ClientHandler]. */
  val requestHandlerMutex = Mutex()
  private val sendMutex = Mutex()

  var remoteSocketAddress: InetSocketAddress? = null
    private set

  suspend fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
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

  private suspend fun handleReceivedInternal(buffer: ByteBuffer) {
    val inBundle: V086Bundle =
      if (CompiledFlags.USE_BYTEREADPACKET_INSTEAD_OF_BYTEBUFFER) {
        try {
          parse(buffer, lastMessageNumber)
        } catch (e: ParseException) {
          // TODO(nue): datagram.packet.toString() doesn't provide any useful information.
          logger
            .atWarning()
            .withCause(e)
            .log("%s failed to parse: %s", this, buffer.dumpBufferFromBeginning())
          null
        } catch (e: V086BundleFormatException) {
          logger
            .atWarning()
            .withCause(e)
            .log("%s received invalid message bundle: %s", this, buffer.dumpBufferFromBeginning())
          null
        } catch (e: MessageFormatException) {
          logger
            .atWarning()
            .withCause(e)
            .log("%s received invalid message: %s}", this, buffer.dumpBufferFromBeginning())
          null
        } finally {
          //             TODO:   datagram.packet.release()
        }
      } else {
        try {
          parse(buffer, lastMessageNumber)
        } catch (e: ParseException) {
          buffer.rewind()
          logger
            .atWarning()
            .withCause(e)
            .log("%s failed to parse: %s", this, EmuUtil.dumpBuffer(buffer))
          null
        } catch (e: V086BundleFormatException) {
          buffer.rewind()
          logger
            .atWarning()
            .withCause(e)
            .log("%s received invalid message bundle: %s", this, EmuUtil.dumpBuffer(buffer))
          null
        } catch (e: MessageFormatException) {
          buffer.rewind()
          logger
            .atWarning()
            .withCause(e)
            .log("%s received invalid message: %s}", this, EmuUtil.dumpBuffer(buffer))
          null
        }
      }
        ?: return

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
           * checked and this causes an error if messageNumber is 0 and lastMessageNumber is 0xFFFF
           * if (messages [i].getNumber() > lastMessageNumber)
           */
          prevMessageNumber = lastMessageNumber
          lastMessageNumber = messages[i]!!.messageNumber
          if (prevMessageNumber + 1 != lastMessageNumber) {
            if (prevMessageNumber == 0xFFFF && lastMessageNumber == 0) {
              // exception; do nothing
            } else {
              logger
                .atWarning()
                .log("%s dropped a packet! (%d to %d)", user, prevMessageNumber, lastMessageNumber)
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
    } catch (e: FatalActionException) {
      logger.atWarning().withCause(e).log("%s fatal action, closing connection", this)
      stop()
    }
  }

  override suspend fun actionPerformed(event: KailleraEvent) {
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

  suspend fun resend(timeoutCounter: Int) {
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

  suspend fun send(outMessage: V086Message?, numToSend: Int = 5) =
    sendMutex.withLock {
      var numToSend = numToSend

      val buffer = getOutBuffer()
      if (outMessage != null) {
        lastMessageBuffer.add(outMessage)
      }
      numToSend = lastMessageBuffer.fill(outMessages, numToSend)
      val outBundle = V086Bundle(outMessages, numToSend)
      stripFromProdBinary { logger.atFinest().log("<- TO P%d: %s", user.id, outMessage) }
      outBundle.writeTo(buffer)
      buffer.flip()
      combinedKailleraController.send(
        DatagramPacket(Unpooled.wrappedBuffer(buffer), remoteSocketAddress!!)
      )
    }

  private val outBuffer =
    ByteBuffer.allocateDirect(flags.v086BufferSize).order(ByteOrder.LITTLE_ENDIAN)

  private fun getOutBuffer(): ByteBuffer = outBuffer.clear()

  init {
    resetGameDataCache()
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
