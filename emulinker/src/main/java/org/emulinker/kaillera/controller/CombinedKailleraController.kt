package org.emulinker.kaillera.controller

import com.codahale.metrics.Counter
import com.google.common.flogger.FluentLogger
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.toJavaAddress
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readByteBuffer
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
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
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.net.BindException
import org.emulinker.net.UdpSocketProvider
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

class CombinedKailleraController
@Inject
constructor(
  private val flags: RuntimeFlags,
  private val accessManager: AccessManager,
  private val threadPool: ThreadPoolExecutor,
  // One for each version (which is only one).
  kailleraServerControllers: @JvmSuppressWildcards Set<KailleraServerController>,
  config: Configuration,
  udpSocketProvider: UdpSocketProvider,
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  @param:Named("listeningOnPortsCounter") private val listeningOnPortsCounter: Counter,
) : Executable {

  var boundPort: Int? = null

  override var threadIsActive = false
    private set

  private var stopFlag = false

  private lateinit var serverSocket: BoundDatagramSocket

  @get:Synchronized
  val isBound: Boolean
    get() = !serverSocket.isClosed

  @Synchronized
  @Throws(BindException::class)
  private fun bind(port: Int, udpSocketProvider: UdpSocketProvider) {
    serverSocket =
      udpSocketProvider.bindSocket(
        io.ktor.network.sockets.InetSocketAddress("0.0.0.0", port),
        bufferSize
      )
    boundPort = port
  }

  fun send(buffer: ByteBuffer, toSocketAddress: InetSocketAddress) {
    if (!isBound) {
      logger
        .atWarning()
        .log("Failed to send to %s: UDPServer is not bound!", formatSocketAddress(toSocketAddress))
      return
    }
    /*
    if(artificalPacketLossPercentage > 0 && Math.abs(random.nextInt()%100) < artificalPacketLossPercentage)
    {
    	return;
    }
    */ try {
      //			logger.atFine().log("send("+EmuUtil.INSTANCE.dumpBuffer(buffer, false)+")");
      // This shouldn't be runblocking probably!
      // I THINK THIS IS THE PROBLEM!!!
      runBlocking {
        serverSocket.send(Datagram(ByteReadPacket(buffer), toSocketAddress.toKtorAddress()))
      }
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port %s", boundPort)
    }
  }

  override fun run() =
    runBlocking(dispatcher) {
      threadIsActive = true
      logger.atFine().log("%s: thread running...", this)
      try {
        listeningOnPortsCounter.inc()
        while (!stopFlag) {
          try {
            val datagram = serverSocket.receive()
            val buffer = datagram.packet.readByteBuffer()
            val fromSocketAddress =
              V086Utils.toJavaAddress(datagram.address as io.ktor.network.sockets.InetSocketAddress)
            if (stopFlag) break
            //          buffer.flip()
            handleReceived(buffer, fromSocketAddress)
            releaseBuffer(buffer)
          } catch (e: SocketException) {
            if (stopFlag) break
            logger.atSevere().withCause(e).log("Failed to receive on port %d", boundPort)
          } catch (e: IOException) {
            if (stopFlag) break
            logger.atSevere().withCause(e).log("Failed to receive on port %d", boundPort)
          }
        }
      } catch (e: Throwable) {
        logger
          .atSevere()
          .withCause(e)
          .log("UDPServer on port %d caught unexpected exception!", boundPort)
        stop()
      } finally {
        listeningOnPortsCounter.dec()
        threadIsActive = false
        logger.atFine().log("%s: thread exiting...", this)
      }
    }

  /** Map of protocol name (e.g. "0.86") to [KailleraServerController]. */
  private val controllersMap = ConcurrentHashMap<String, KailleraServerController>()

  val clientHandlers = ConcurrentHashMap<InetSocketAddress, V086ClientHandler>()

  private val inBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)
  private val outBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)

  val bufferSize: Int = flags.v086BufferSize

  val dispatcher = threadPool.asCoroutineDispatcher()
  private val context = CoroutineScope(dispatcher)
  //  val portListenerThread = Executors.newSingleThreadExecutor()

  fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    // TRY YIELDING??
    context.launch {
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
                  formatSocketAddress(remoteSocketAddress),
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
              formatSocketAddress(remoteSocketAddress)
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
              .log("Sending server full response to %s", formatSocketAddress(remoteSocketAddress))
            return@launch
          } catch (e: NewConnectionException) {
            logger.atSevere().withCause(e).log("LALALALA")
            return@launch
          }

        clientHandlers[remoteSocketAddress] = handler!!
      }
      handler.mutex.withLock { handler.handleReceived(buffer, remoteSocketAddress) }
    }
  }

  override fun stop() {
    if (stopFlag) return
    for (controller in controllersMap.values) {
      controller.stop()
    }

    stopFlag = true
    serverSocket.close()
  }

  fun allocateIncomingBuffer(): ByteBuffer {
    // return ByteBufferMessage.getBuffer(bufferSize);
    //    logger.atSevere().log("NEW IN BUFFER")
    //    inBuffer.clear()
    //    return inBuffer

    return ByteBuffer.allocateDirect(flags.v086BufferSize).also {
      it.order(ByteOrder.LITTLE_ENDIAN)
    }
  }

  fun releaseBuffer(buffer: ByteBuffer) {
    // ByteBufferMessage.releaseBuffer(buffer);
    // buffer.clear();
  }

  @Synchronized
  fun start() {
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
    bind(port, udpSocketProvider)
    logger.atInfo().log("Ready to accept connections on port %d", port)
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
