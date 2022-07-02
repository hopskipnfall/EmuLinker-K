package org.emulinker.net

import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.lang.Exception
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.Throws
import kotlinx.coroutines.Dispatchers
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

private val logger = FluentLogger.forEnclosingClass()

abstract class UDPServer(shutdownOnExit: Boolean, metrics: MetricRegistry?) : Executable {
  abstract val bufferSize: Int

  /*
  	private static int		artificalPacketLossPercentage = 0;
  	private static int		artificalDelay = 0;
  	private static Random	random = new Random();

  	static
  	{
  		try
  		{
  			artificalPacketLossPercentage = Integer.parseInt(System.getProperty("artificalPacketLossPercentage"));
  			artificalDelay = Integer.parseInt(System.getProperty("artificalDelay"));
  		}
  		catch(Exception e) {}

  		if(artificalPacketLossPercentage > 0)
  			logger.atWarning().log("Introducing " + artificalPacketLossPercentage + "% artifical packet loss!");

  		if(artificalDelay > 0)
  			logger.atWarning().log("Introducing " + artificalDelay + "ms artifical delay!");
  	}
  */
  var bindPort = 0
    private set

  private var serverSocket: BoundDatagramSocket? = null

  final override var threadIsActive = false
    private set

  protected var stopFlag = false
    private set

  @get:Synchronized
  val isBound: Boolean
    get() {
      return serverSocket != null && !serverSocket!!.isClosed
    }
  val isConnected: Boolean
    get() = serverSocket != null && !serverSocket!!.isClosed

  @Synchronized
  open suspend fun start() {
    logger.atFine().log(toString() + " received start request!")
    if (threadIsActive) {
      logger.atFine().log(toString() + " start request ignored: already running!")
      return
    }
    stopFlag = false
  }

  @Synchronized
  override suspend fun stop() {
    stopFlag = true
    serverSocket?.close()
  }

  @Synchronized
  @Throws(BindException::class)
  protected fun bind() {
    bind(-1)
  }

  @Synchronized
  @Throws(BindException::class)
  protected open fun bind(port: Int) {
    val selectorManager = SelectorManager(Dispatchers.IO)
    serverSocket =
        aSocket(selectorManager)
            .udp()
            .configure {
              receiveBufferSize = bufferSize
              sendBufferSize = bufferSize
            //              typeOfService = TypeOfService.IPTOS_LOWDELAY
            }
            .bind(io.ktor.network.sockets.InetSocketAddress("127.0.0.1", port))

    logger.atInfo().log("Accepting messages at ${serverSocket!!.localAddress}")
  }

  protected abstract fun allocateBuffer(): ByteBuffer

  protected abstract fun releaseBuffer(buffer: ByteBuffer)

  protected abstract suspend fun handleReceived(
      buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress
  )

  protected suspend fun send(buffer: ByteBuffer, toSocketAddress: InetSocketAddress) {
    if (!isBound) {
      logger
          .atWarning()
          .log(
              "Failed to send to " +
                  formatSocketAddress(toSocketAddress) +
                  ": UDPServer is not bound!")
      return
    }
    /*
    if(artificalPacketLossPercentage > 0 && Math.abs(random.nextInt()%100) < artificalPacketLossPercentage)
    {
    	return;
    }
    */ try {
      //			logger.atFine().log("send("+EmuUtil.INSTANCE.dumpBuffer(buffer, false)+")");
      //      channel!!.send(buffer, toSocketAddress)
      serverSocket!!.send(
          Datagram(ByteReadPacket(buffer), V086Utils.toKtorAddress(toSocketAddress)))
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port $bindPort")
    }
  }

  override suspend fun run() {
    threadIsActive = true

    while (!stopFlag) {
      val datagram = serverSocket!!.incoming.receive()

      require(datagram.address is io.ktor.network.sockets.InetSocketAddress) {
        "address was an incompatable type!"
      }

      val buffer = datagram.packet.readByteBuffer()
      handleReceived(
          buffer,
          V086Utils.toJavaAddress(datagram.address as io.ktor.network.sockets.InetSocketAddress))
      releaseBuffer(buffer)
    }

    threadIsActive = false
  }

  // TODO(nue): Investigate this.
  //  private inner class ShutdownThread : Thread() {
  //    override fun run() {
  //      this@UDPServer.stop()
  //    }
  //  }
  //  init {
  //    if (shutdownOnExit) Runtime.getRuntime().addShutdownHook(ShutdownThread())
  //  }
}
