package org.emulinker.net

import com.google.common.flogger.FluentLogger
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.Exception
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

abstract class UDPServer : Executable {
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
  // TODO(nue): This is supposed to be the server's port i guess but is always 0..
  val bindPort = 0

  private lateinit var serverSocket: BoundDatagramSocket

  protected lateinit var globalContext: CoroutineContext

  final override var threadIsActive = false
    private set

  protected var stopFlag = false
    private set

  @get:Synchronized
  val isBound: Boolean
    get() {
      return !serverSocket.isClosed
    }

  @Synchronized
  open suspend fun start(udpSocketProvider: UdpSocketProvider, globalContext: CoroutineContext) {
    this.globalContext = globalContext
    logger.atFine().log("%s received start request!", this)
    if (threadIsActive) {
      logger.atFine().log("%s start request ignored: already running!", this)
      return
    }
    stopFlag = false
  }

  @Synchronized
  override suspend fun stop() {
    stopFlag = true
    serverSocket.dispose()
  }

  @Synchronized
  protected open fun bind(udpSocketProvider: UdpSocketProvider, port: Int) {
    serverSocket =
      udpSocketProvider.bindSocket(
        io.ktor.network.sockets.InetSocketAddress("0.0.0.0", port),
        bufferSize
      )

    logger.atInfo().log("Accepting messages at %s", serverSocket.localAddress)
  }

  protected abstract fun allocateBuffer(): ByteBuffer

  /**
   * Handler for the request.
   *
   * Note that as this is communication over a socket and we do not send back a response, the client
   * isn't waiting on a response message. That being said we do only handle one request per user so
   * deadlocks are possible.
   */
  protected abstract suspend fun handleReceived(
    buffer: ByteBuffer,
    remoteSocketAddress: InetSocketAddress
  )

  protected suspend fun send(buffer: ByteBuffer, toSocketAddress: InetSocketAddress) {
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
    */
    try {
      serverSocket.send(Datagram(ByteReadPacket(buffer), toSocketAddress.toKtorAddress()))
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port %s", bindPort)
    }
  }

  override suspend fun run(globalContext: CoroutineContext) {
    this.globalContext = globalContext
    threadIsActive = true

    while (!stopFlag) {
      supervisorScope {
        val datagram = serverSocket.incoming.receive()
        val buffer = datagram.packet.readByteBuffer()

        try {
          handleReceived(
            buffer,
            V086Utils.toJavaAddress(datagram.address as io.ktor.network.sockets.InetSocketAddress),
          )
        } catch (e: Exception) {
          if (e is CancellationException) {
            throw e
          }
          logger
            .atSevere()
            .withCause(e)
            .log("Error while handling request: %s", buffer.dumpBufferFromBeginning())
        }
      }
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

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
