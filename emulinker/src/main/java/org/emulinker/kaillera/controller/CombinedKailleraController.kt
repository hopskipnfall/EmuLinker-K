package org.emulinker.kaillera.controller

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Set as JavaSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PING
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PONG
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.net.BindException
import org.emulinker.net.UDPServer
import org.emulinker.util.EmuUtil

class CombinedKailleraController
@Inject
constructor(
  private val metrics: MetricRegistry,
  private val flags: RuntimeFlags,
  private val accessManager: AccessManager,
  private val threadPool: ThreadPoolExecutor,
  // One for each version (which is only one).
  kailleraServerControllers: JavaSet<KailleraServerController>,
  config: Configuration,
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter,
) : UDPServer(shutdownOnExit = false, listeningOnPortsCounter) {

  /** Map of protocol name (e.g. "0.86") to [KailleraServerController]. */
  private val controllersMap = ConcurrentHashMap<String, KailleraServerController>()

  val clientHandlers = ConcurrentHashMap<InetSocketAddress, V086ClientHandler>()

  private val inBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)
  private val outBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)

  override fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    scope.launch {
      var handler = clientHandlers[remoteSocketAddress]
      if (handler == null) {
        // User is new. It's either a ConnectMessage or it's the user's first message after
        // reconnecting to the server via the dictated port.
        val connectMessageResult: Result<ConnectMessage> = ConnectMessage.parse(buffer)
        if (connectMessageResult.isSuccess) {
          when (val connectMessage = connectMessageResult.getOrThrow()) {
            is ConnectMessage_PING -> {
              send(ConnectMessage_PONG().toBuffer(), remoteSocketAddress)
            }
            is RequestPrivateKailleraPortRequest -> {
              check(connectMessage.protocol == "0.83") {
                "Client listed unsupported protocol! $connectMessage"
              }

              send(RequestPrivateKailleraPortResponse(boundPort!!).toBuffer(), remoteSocketAddress)
            }
            else -> {
              logger
                .atWarning()
                .log(
                  "Received unexpected message type from %s: %s",
                  EmuUtil.formatSocketAddress(remoteSocketAddress),
                  connectMessageResult
                )
            }
          }
          // We successfully parsed a connection message and handled it so return.
          return@launch
        }

        // The message should be parsed as a V086Message. Reset it.
        buffer.position(0)

        if (!accessManager.isAddressAllowed(remoteSocketAddress.address)) {
          logger
            .atWarning()
            .log(
              "AccessManager denied connection from %s",
              EmuUtil.formatSocketAddress(remoteSocketAddress)
            )
          return@launch
        }

        handler =
          try {
            val protocolController: KailleraServerController =
              controllersMap.elements().nextElement()
            // TODO(nue): Don't hardcode this.
            protocolController.newConnection(
              remoteSocketAddress,
              "v086",
              this@CombinedKailleraController
            )
          } catch (e: ServerFullException) {
            logger
              .atFine()
              .withCause(e)
              .log(
                "Sending server full response to %s",
                EmuUtil.formatSocketAddress(remoteSocketAddress)
              )
            return@launch
          } catch (e: NewConnectionException) {
            logger.atSevere().withCause(e).log("LALALALA")
            return@launch
          }

        clientHandlers[remoteSocketAddress] = handler!!
      }

      if (handler.user.game == null) {
        // While logging in we need a mutex.
        handler.mutex.withLock { handler.handleReceived(buffer, remoteSocketAddress) }
      } else {
        // When in the game it's unlikely we'll be processing multiple messages from the same user
        // at the same time.
        // Bypassing the mutex might save some speed by removing a suspension point.
        handler.handleReceived(buffer, remoteSocketAddress)
      }
    }
  }

  @Throws(BindException::class)
  public override fun bind(port: Int) {
    super.bind(port)
  }

  override fun stop() {
    if (stopFlag) return
    for (controller in controllersMap.values) {
      controller.stop()
    }
    super.stop()
  }

  override fun allocateIncomingBuffer(): ByteBuffer {
    // return ByteBufferMessage.getBuffer(bufferSize);
    //    logger.atSevere().log("NEW IN BUFFER")
    //    inBuffer.clear()
    //    return inBuffer

    return ByteBuffer.allocateDirect(flags.v086BufferSize).also {
      it.order(ByteOrder.LITTLE_ENDIAN)
    }
  }

  override fun releaseBuffer(buffer: ByteBuffer) {
    // ByteBufferMessage.releaseBuffer(buffer);
    // buffer.clear();
  }

  @Synchronized
  override fun start() {
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
  }

  init {
    for (controller in kailleraServerControllers) {
      val clientTypes = controller.clientTypes
      for (j in clientTypes.indices) {
        controllersMap[clientTypes[j]] = controller
      }
    }

    inBuffer.order(ByteOrder.LITTLE_ENDIAN)
    outBuffer.order(ByteOrder.LITTLE_ENDIAN)

    // TODO(nue): RuntimeFlags.
    //    private val extraPorts: Int = config.getInt("controllers.v086.extraPorts", 0)
    //    val port = config.getInt("controllers.v086.portRangeStart")
    val port = config.getInt("controllers.connect.port")
    super.bind(port)
    logger.atInfo().log("Ready to accept connections on port %d", port)
  }

  private val scope = CoroutineScope(threadPool.asCoroutineDispatcher())

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
