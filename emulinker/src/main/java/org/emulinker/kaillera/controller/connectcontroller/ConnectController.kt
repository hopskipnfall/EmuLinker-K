package org.emulinker.kaillera.controller.connectcontroller

import com.codahale.metrics.Counter
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Set as JavaSet
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class ConnectController
@Inject
internal constructor(
  // TODO(nue): This makes no sense because KailleraServerController is a singleton...
  kailleraServerControllers: JavaSet<KailleraServerController>,
  private val accessManager: AccessManager,
  private val config: Configuration,
  flags: RuntimeFlags,
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter,
) : UDPServer(listeningOnPortsCounter) {

  private val mutex = Mutex()

  private val controllersMap: MutableMap<String, KailleraServerController> = HashMap()

  init {
    kailleraServerControllers.forEach { controller ->
      controller.clientTypes.forEach { type ->
        logger.atFine().log("Mapping client type %s to %s", type, controller)
        controllersMap[type] = controller
      }
    }
  }

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

  private fun getController(clientType: String?): KailleraServerController? {
    return controllersMap[clientType]
  }

  val controllers: Collection<KailleraServerController>
    get() = controllersMap.values

  override fun allocateBuffer(): ByteBuffer {
    return getBuffer(internalBufferSize)
  }

  override fun toString(): String =
    if (boundPort != null) "ConnectController($boundPort)" else "ConnectController(unbound)"

  override suspend fun start(
    udpSocketProvider: UdpSocketProvider,
    globalContext: CoroutineContext
  ) {
    this.udpSocketProvider = udpSocketProvider
    this.globalContext = globalContext
    val port = config.getInt("controllers.connect.port")
    startTime = System.currentTimeMillis()

    super.bind(udpSocketProvider, port)
    this.run(globalContext)
  }

  override suspend fun stop() {
    mutex.withLock {
      super.stop()
      for (controller in controllersMap.values) controller.stop()
    }
  }

  override suspend fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    requestCount++
    val formattedSocketAddress = formatSocketAddress(remoteSocketAddress)
    // TODO(nue): Remove this catch logic.
    val inMessage: ConnectMessage =
      try {
        parse(buffer)
      } catch (e: MessageFormatException) {
        messageFormatErrorCount++
        buffer.rewind()
        logger
          .atWarning()
          .log("Received invalid message from %s: %s", formattedSocketAddress, dumpBuffer(buffer))
        return
      } catch (e: IllegalArgumentException) {
        messageFormatErrorCount++
        buffer.rewind()
        logger
          .atWarning()
          .log("Received invalid message from %s: %s", formattedSocketAddress, dumpBuffer(buffer))
        return
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
        mutex.withLock {
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
        }
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

  private suspend fun send(outMessage: ConnectMessage, toSocketAddress: InetSocketAddress) {
    debugLog {
      logger.atFinest().log("<- TO %s: %s", formatSocketAddress(toSocketAddress), outMessage)
    }

    send(outMessage.toBuffer(), toSocketAddress)
    outMessage.releaseBuffer()
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
