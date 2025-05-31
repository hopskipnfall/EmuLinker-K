package org.emulinker.kaillera.controller.v086

import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import io.netty.buffer.ByteBuf
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.action.FatalActionException
import org.emulinker.kaillera.controller.v086.action.GameDataAction
import org.emulinker.kaillera.controller.v086.action.V086Action
import org.emulinker.kaillera.controller.v086.action.V086GameEventHandler
import org.emulinker.kaillera.controller.v086.action.V086ServerEventHandler
import org.emulinker.kaillera.controller.v086.action.V086UserEventHandler
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle.Companion.parse
import org.emulinker.kaillera.controller.v086.protocol.V086BundleFormatException
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.KailleraEventListener
import org.emulinker.kaillera.model.event.ServerEvent
import org.emulinker.kaillera.model.event.StopFlagEvent
import org.emulinker.kaillera.model.event.UserEvent
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning
import org.emulinker.util.EmuUtil.timeKt
import org.emulinker.util.GameDataCache
import org.emulinker.util.GameDataCacheImpl
import org.emulinker.util.stripFromProdBinary
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class V086ClientHandler(
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  val connectRemoteSocketAddress: InetSocketAddress,
  val controller: V086Controller,
  /** The [CombinedKailleraController] that created this instance. */
  private val combinedKailleraController: CombinedKailleraController,
) : KailleraEventListener, KoinComponent {
  private val metrics: MetricRegistry by inject()
  private val flags: RuntimeFlags by inject()

  /** Mutex ensuring that only one packet is processed at a time for this [V086ClientHandler]. */
  val requestHandlerMutex = Object()
  private val sendMutex = Object()

  var remoteSocketAddress: InetSocketAddress? = null
    private set

  fun handleReceived(buf: ByteBuf, remoteSocketAddress: InetSocketAddress) {
    if (this.remoteSocketAddress == null) {
      this.remoteSocketAddress = remoteSocketAddress
    } else if (remoteSocketAddress != this.remoteSocketAddress) {
      logger
        .atSevere()
        .log(
          "Rejecting packet received from wrong address: %s != %s. This should not be possible!",
          EmuUtil.formatSocketAddress(remoteSocketAddress),
          EmuUtil.formatSocketAddress(this.remoteSocketAddress!!),
        )
      return
    }
    if (flags.metricsEnabled) {
      clientRequestTimer.timeKt { handleReceivedInternal(buf) }
    } else {
      handleReceivedInternal(buf)
    }
  }

  lateinit var user: KailleraUser
    private set

  private var messageNumberCounter = 0

  // TODO(nue): Add this to RuntimeFlags and increase to at least 5.
  val numAcksForSpeedTest = 3

  private var prevMessageNumber = -1

  private var lastMessageNumber = -1

  val clientGameDataCache: GameDataCache = GameDataCacheImpl(256)

  val serverGameDataCache: GameDataCache = GameDataCacheImpl(256)

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

  @get:Synchronized
  val nextMessageNumber: Int
    get() {
      if (messageNumberCounter > 0xFFFF) messageNumberCounter = 0
      return messageNumberCounter++
    }

  fun resetGameDataCache() {
    clientGameDataCache.clear()
    serverGameDataCache.clear()
  }

  fun startSpeedTest() {
    lastMeasurement = System.nanoTime()
    testStart = lastMeasurement
    speedMeasurementCount = 0
  }

  fun addSpeedMeasurement() {
    val et = (System.nanoTime() - lastMeasurement).toInt()
    if (et < bestNetworkSpeed) {
      bestNetworkSpeed = et
    }
    speedMeasurementCount++
    lastMeasurement = System.nanoTime()
  }

  val averageNetworkSpeed: Duration
    get() = (lastMeasurement - testStart).nanoseconds / speedMeasurementCount

  fun start(user: KailleraUser) {
    this.user = user
    controller.clientHandlers[user.id] = this
  }

  override fun stop() {
    controller.clientHandlers.remove(user.id)
    combinedKailleraController.clientHandlers.remove(remoteSocketAddress)
  }

  private fun handleReceivedInternal(buffer: ByteBuf) {
    val inBundle: V086Bundle =
      if (CompiledFlags.USE_BYTEREADPACKET_INSTEAD_OF_BYTEBUFFER) {
        // Note: This is currently DISABLED as it's unstable (see tests marked as @Ignore).
        try {
          parse(buffer, lastMessageNumber)
        } catch (e: ParseException) {
          // TODO(nue): datagram.packet.toString() doesn't provide any useful information.
          logger
            .atWarning()
            .withCause(e)
            .log("%s failed to parse: %s", this, buffer.nioBuffer().dumpBufferFromBeginning())
          null
        } catch (e: V086BundleFormatException) {
          logger
            .atWarning()
            .withCause(e)
            .log(
              "%s received invalid message bundle: %s",
              this,
              buffer.nioBuffer().dumpBufferFromBeginning(),
            )
          null
        } catch (e: MessageFormatException) {
          logger
            .atWarning()
            .withCause(e)
            .log(
              "%s received invalid message: %s}",
              this,
              buffer.nioBuffer().dumpBufferFromBeginning(),
            )
          null
        } finally {
          //             TODO:   datagram.packet.release()
        }
      } else {
        val newBuffer: ByteBuffer = buffer.nioBuffer()
        try {
          parse(newBuffer, lastMessageNumber)
        } catch (e: ParseException) {
          newBuffer.position(0)
          logger
            .atWarning()
            .withCause(e)
            .log("%s failed to parse: %s", this, EmuUtil.dumpBuffer(buffer.nioBuffer()))
          null
        } catch (e: V086BundleFormatException) {
          newBuffer.position(0)
          logger
            .atWarning()
            .withCause(e)
            .log(
              "%s received invalid message bundle: %s",
              this,
              EmuUtil.dumpBuffer(buffer.nioBuffer()),
            )
          null
        } catch (e: MessageFormatException) {
          newBuffer.position(0)
          logger
            .atWarning()
            .withCause(e)
            .log("%s received invalid message: %s}", this, EmuUtil.dumpBuffer(newBuffer))
          null
        }
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
      val messages = inBundle.messages
      // TODO(nue): Combine these two cases? This seems unnecessary.
      if (inBundle.numMessages == 1) {
        val m: V086Message = messages[0]!!
        lastMessageNumber = m.messageNumber
        val messageTypeId = m.messageTypeId

        val action: V086Action<out V086Message>? =
          // Checking for GameData first is a speed optimization.
          if (messageTypeId == GameData.ID) {
            GameDataAction
          } else {
            controller.actions[m.messageTypeId.toInt()]
          }
        if (action == null) {
          logger
            .atSevere()
            .log("No action defined to handle client message: %s", messages.firstOrNull())
          return
        }
        (action as V086Action<V086Message>).performAction(m, this)
      } else {
        // read the bundle from back to front to process the oldest messages first
        for (i in inBundle.numMessages - 1 downTo 0) {
          /**
           * already extracts messages with higher numbers when parsing, it does not need to be
           * checked and this causes an error if messageNumber is 0 and lastMessageNumber is 0xFFFF
           * if (messages [i].getNumber() > lastMessageNumber)
           */
          prevMessageNumber = lastMessageNumber
          val m: V086Message = messages[i]!!
          lastMessageNumber = m.messageNumber
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
          val messageTypeId = m.messageTypeId
          val action: V086Action<out V086Message>? =
            // Checking for GameData first is a speed optimization.
            if (messageTypeId == GameData.ID) {
              GameDataAction
            } else {
              controller.actions[m.messageTypeId.toInt()]
            }
          if (action == null) {
            logger.atSevere().log("No action defined to handle client message: %s", messages[i])
          } else {
            // logger.atFine().log(user + " -> " + message);
            (action as V086Action<V086Message>).performAction(m, this)
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
      // Check for GameDataEvent first to avoid map lookup slowness.
      is GameDataEvent -> {
        GameDataAction.handleEvent(event, this)
      }
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
              event,
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

  // Caching here for speed.
  private val maxPingMs: Long = flags.maxPing.inWholeMilliseconds

  fun resend(timeoutCounter: Int) {
    // if ((System.currentTimeMillis() - lastResend) > (user.getPing()*3))
    if (System.currentTimeMillis() - lastResend > maxPingMs) {
      // int numToSend = (3+timeoutCounter);
      var numToSend = 3 * timeoutCounter
      if (numToSend > V086Controller.MAX_BUNDLE_SIZE) numToSend = V086Controller.MAX_BUNDLE_SIZE
      logger.atFine().log("%s: resending last %d messages", this, numToSend)
      resendFromCache(numToSend)
      lastResend = System.currentTimeMillis()
    } else {
      logger.atFine().log("Skipping resend...")
    }
  }

  private fun resendFromCache(numToSend: Int = 5) {
    synchronized(sendMutex) {
      var numToSend = numToSend

      val buf =
        combinedKailleraController.nettyChannel
          .alloc()
          .directBuffer(flags.v086BufferSize)
          .order(ByteOrder.LITTLE_ENDIAN)
      numToSend = lastMessageBuffer.fill(outMessages, numToSend)
      val outBundle = V086Bundle(outMessages, numToSend)
      stripFromProdBinary { logger.atFinest().log("<- TO P%d: (RESEND)", user.id) }
      outBundle.writeTo(buf)
      combinedKailleraController.send(DatagramPacket(buf, remoteSocketAddress!!))
    }
  }

  fun send(outMessage: V086Message, numToSend: Int = 5) {
    synchronized(sendMutex) {
      var numToSend = numToSend

      val buf =
        combinedKailleraController.nettyChannel
          .alloc()
          .directBuffer(flags.v086BufferSize)
          .order(ByteOrder.LITTLE_ENDIAN)
      lastMessageBuffer.add(outMessage)
      numToSend = lastMessageBuffer.fill(outMessages, numToSend)
      val outBundle = V086Bundle(outMessages, numToSend)
      stripFromProdBinary { logger.atFinest().log("<- TO P%d: %s", user.id, outMessage) }
      outBundle.writeTo(buf)
      combinedKailleraController.send(DatagramPacket(buf, remoteSocketAddress!!))
    }
  }

  init {
    resetGameDataCache()
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
