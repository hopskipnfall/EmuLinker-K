package org.emulinker.kaillera.controller.connectcontroller

import com.codahale.metrics.Counter
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import org.apache.commons.configuration.Configuration
import org.emulinker.kaillera.controller.connectcontroller.protocol.*
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage.Companion.parse
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage.Companion.getBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.net.UDPServer
import org.emulinker.util.EmuUtil.dumpBuffer
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.LoggingUtils.debugLog

/**
 * The UDP Server implementation.
 *
 * This is the main server for new connections (usually on 27888).
 */
@Singleton
// Open for testing.
open class ConnectController
@Inject
internal constructor(
  private val threadPool: ThreadPoolExecutor,
  private val config: Configuration,
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter
) : UDPServer(/* shutdownOnExit= */ true, listeningOnPortsCounter) {

  var bufferSize = 0
  var startTime: Long = 0
    private set
  var requestCount = 0
    private set
  var messageFormatErrorCount = 0
    private set
  private var pingCount = 0

  override fun allocateIncomingBuffer(): ByteBuffer = getBuffer(bufferSize)

  override fun releaseBuffer(buffer: ByteBuffer) {}

  override fun toString(): String =
    if (boundPort != null) "ConnectController($boundPort)" else "ConnectController(unbound)"

  @Synchronized
  override fun start() {
    startTime = System.currentTimeMillis()
    logger
      .atFine()
      .log(
        "%s Thread starting (ThreadPool:%d/%d)",
        this,
        threadPool.activeCount,
        threadPool.poolSize
      )
    threadPool.execute(this)
    Thread.yield()
    logger
      .atFine()
      .log(
        "%s Thread starting (ThreadPool:%d/%d)",
        this,
        threadPool.activeCount,
        threadPool.poolSize
      )
  }

  @Synchronized
  override fun stop() {
    super.stop()
  }

  @Synchronized
  override fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    requestCount++
    val formattedSocketAddress = formatSocketAddress(remoteSocketAddress)
    val inMessage: ConnectMessage? =
      try {
        parse(buffer)
      } catch (e: Exception) {
        when (e) {
          is MessageFormatException,
          is IllegalArgumentException -> {
            messageFormatErrorCount++
            buffer.rewind()
            logger
              .atWarning()
              .log(
                "Received invalid message from %s: %s",
                formattedSocketAddress,
                dumpBuffer(buffer)
              )
            return
          }
          else -> throw e
        }
      }

    debugLog { logger.atFinest().log("-> FROM %s: %s", formattedSocketAddress, inMessage) }

    when (inMessage) {
      is ConnectMessage_PING -> {
        pingCount++
        send(ConnectMessage_PONG(), remoteSocketAddress)
        return
      }
      is RequestPrivateKailleraPortRequest -> {
        check(inMessage.protocol == "0.83") { "Client listed unsupported protocol! $inMessage" }

        send(
          RequestPrivateKailleraPortResponse(config.getInt("controllers.v086.portRangeStart")),
          remoteSocketAddress
        )
      }
      else -> {
        messageFormatErrorCount++
        logger
          .atWarning()
          .log("Received unexpected message type from %s: %s", formattedSocketAddress, inMessage)
        return
      }
    }
  }

  private fun send(outMessage: ConnectMessage, toSocketAddress: InetSocketAddress) {
    debugLog {
      logger.atFinest().log("<- TO %s: %s", formatSocketAddress(toSocketAddress), outMessage)
    }

    send(outMessage.toBuffer(), toSocketAddress)
  }

  init {
    //    // TODO(nue): RuntimeFlags.
    //    val port = config.getInt("controllers.connect.port")
    //    bufferSize = config.getInt("controllers.connect.bufferSize")
    //    require(bufferSize > 0) { "controllers.connect.bufferSize must be > 0" }
    //    try {
    //      super.bind(port)
    //    } catch (e: BindException) {
    //      throw IllegalStateException(e)
    //    }
    //    logger.atInfo().log("Ready to accept connections on port %d", port)
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
