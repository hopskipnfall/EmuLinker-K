package org.emulinker.kaillera.controller

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.net.BindException
import org.emulinker.net.UDPServer
import org.emulinker.util.EmuUtil

class CombinedKailleraController
@Inject
constructor(
  metrics: MetricRegistry,
  private val flags: RuntimeFlags,
  private val accessManager: AccessManager,
  private val threadPool: ThreadPoolExecutor,
  // One for each version (which is only one).
  kailleraServerControllers: Set<KailleraServerController>,
  config: Configuration,
  // TODO(nue): Try to replace this with remoteSocketAddress.
  /** I think this is the address from when the user called the connect controller. */
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter,
) : UDPServer(shutdownOnExit = false, listeningOnPortsCounter) {

  /** Map of protocol name (e.g. "0.86") to [KailleraServerController]. */
  private val controllersMap = ConcurrentHashMap<String, KailleraServerController>()

  private val clientHandlers = ConcurrentHashMap<InetSocketAddress, V086ClientHandler>()

  private val inBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)
  private val outBuffer: ByteBuffer = ByteBuffer.allocateDirect(flags.v086BufferSize)

  override fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    scope.launch {
      logger
        .atSevere()
        .log(
          "RECEIVED STARTED AND THE POSITION IS ${buffer.position()}----------------------------------"
        )
      var handler = clientHandlers[remoteSocketAddress]
      if (handler == null) {
        // User is new.
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

      synchronized(handler) { handler.handleReceived(buffer, remoteSocketAddress) }
      logger.atSevere().log("RECEIVED FINISHED----------------------------------")
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
    logger.atSevere().log("RELEASING BUFFER")
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
    val port = config.getInt("controllers.v086.portRangeStart")
    super.bind(port)
    logger.atInfo().log("Ready to accept connections on port %d", port)
  }

  private val scope = CoroutineScope(Dispatchers.IO)

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
