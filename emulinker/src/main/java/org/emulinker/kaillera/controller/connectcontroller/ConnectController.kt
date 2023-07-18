package org.emulinker.kaillera.controller.connectcontroller

import com.codahale.metrics.Counter
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Set as JavaSet
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.KailleraServerController
import org.emulinker.kaillera.controller.connectcontroller.protocol.*
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage.Companion.parse
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage.Companion.getBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.net.UDPServer
import org.emulinker.net.UdpSocketProvider
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
  // TODO(nue): This makes no sense because KailleraServerController is a singleton...
  kailleraServerControllers: JavaSet<KailleraServerController>,
  private val accessManager: AccessManager,
  private val config: Configuration,
  flags: RuntimeFlags,
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter,
) : UDPServer(listeningOnPortsCounter) {

  private val controllersMap: MutableMap<String?, KailleraServerController>

  override val bufferSize = flags.connectControllerBufferSize

  private var internalBufferSize = 0
  private var startTime: Long = 0
  private var requestCount = 0
  private var messageFormatErrorCount = 0
  private var protocolErrorCount = 0
  private var deniedServerFullCount = 0
  private var deniedOtherCount = 0
  private var lastAddress: String? = null
  private var lastAddressCount = 0
  private var failedToStartCount = 0
  private var connectCount = 0
  private var pingCount = 0

  private lateinit var udpSocketProvider: UdpSocketProvider

  fun getController(clientType: String?): KailleraServerController? {
    return controllersMap[clientType]
  }

  val controllers: Collection<KailleraServerController>
    get() = controllersMap.values

  override fun allocateBuffer(): ByteBuffer {
    return getBuffer(internalBufferSize)
  }

  override fun toString(): String =
    if (boundPort != null) "ConnectController($boundPort)" else "ConnectController(unbound)"

  @Synchronized
  override fun start(udpSocketProvider: UdpSocketProvider) {
    this.udpSocketProvider = udpSocketProvider
    startTime = System.currentTimeMillis()
    logger
      .atFine()
      .log(
        "%s Thread starting (ThreadPool:%d/%d)",
        this,
        threadPool.activeCount,
        threadPool.poolSize
      )
    val port = config.getInt("controllers.connect.port")
    super.bind(udpSocketProvider, port)
    logger.atInfo().log("Ready to accept connections on port %d", port)
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
    for (controller in controllersMap.values) controller.stop()
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

    // the message set of the ConnectController isn't really complex enough to warrant a complicated
    // request/action class
    // structure, so I'm going to handle it  all in this class alone
    if (inMessage is ConnectMessage_PING) {
      pingCount++
      send(ConnectMessage_PONG(), remoteSocketAddress)
      return
    }
    if (inMessage !is RequestPrivateKailleraPortRequest) {
      messageFormatErrorCount++
      logger
        .atWarning()
        .log("Received unexpected message type from %s: %s", formattedSocketAddress, inMessage)
      return
    }

    // now we need to find the specific server this client is request to
    // connect to using the client type
    val protocolController = getController(inMessage.protocol)
    if (protocolController == null) {
      protocolErrorCount++
      logger
        .atSevere()
        .log(
          "Client requested an unhandled protocol %s: %s",
          formattedSocketAddress,
          inMessage.protocol
        )
      return
    }
    if (!accessManager.isAddressAllowed(remoteSocketAddress.address)) {
      deniedOtherCount++
      logger.atWarning().log("AccessManager denied connection from %s", formattedSocketAddress)
      return
    } else {
      val privatePort: Int
      val access = accessManager.getAccess(remoteSocketAddress.address)
      try {
        // SF MOD - Hammer Protection
        if (access < AccessManager.ACCESS_ADMIN && connectCount > 0) {
          if (lastAddress == remoteSocketAddress.address.hostAddress) {
            lastAddressCount++
            if (lastAddressCount >= 4) {
              lastAddressCount = 0
              failedToStartCount++
              logger
                .atInfo()
                .log("SF MOD: HAMMER PROTECTION (2 Min Ban): %s", formattedSocketAddress)
              accessManager.addTempBan(remoteSocketAddress.address.hostAddress, 2.minutes)
              return
            }
          } else {
            lastAddress = remoteSocketAddress.address.hostAddress
            lastAddressCount = 0
          }
        } else lastAddress = remoteSocketAddress.address.hostAddress
        privatePort =
          protocolController.newConnection(
            udpSocketProvider,
            remoteSocketAddress,
            inMessage.protocol
          )
        if (privatePort <= 0) {
          failedToStartCount++
          logger
            .atSevere()
            .log("%s failed to start for %s", protocolController, formattedSocketAddress)
          return
        }
        connectCount++
        logger
          .atFine()
          .log(
            "%s allocated port %d to client from %s",
            protocolController,
            privatePort,
            remoteSocketAddress.address.hostAddress
          )
        send(RequestPrivateKailleraPortResponse(privatePort), remoteSocketAddress)
      } catch (e: ServerFullException) {
        deniedServerFullCount++
        logger
          .atFine()
          .withCause(e)
          .log("Sending server full response to %s", formattedSocketAddress)
        send(ConnectMessage_TOO(), remoteSocketAddress)
        return
      } catch (e: NewConnectionException) {
        deniedOtherCount++
        logger
          .atWarning()
          .withCause(e)
          .log("%s denied connection from %s", protocolController, formattedSocketAddress)
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
    require(bufferSize > 0) { "controllers.connect.bufferSize must be > 0" }
    controllersMap = HashMap()
    for (controller in kailleraServerControllers) {
      val clientTypes = controller.clientTypes
      for (j in clientTypes.indices) {
        logger.atFine().log("Mapping client type %s to %s", clientTypes[j], controller)
        controllersMap[clientTypes[j]] = controller
      }
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
