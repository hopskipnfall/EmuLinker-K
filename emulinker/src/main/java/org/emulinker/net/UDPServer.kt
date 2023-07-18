package org.emulinker.net

import com.codahale.metrics.Counter
import com.google.common.flogger.FluentLogger
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readByteBuffer
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

abstract class UDPServer(private val listeningOnPortsCounter: Counter) : Executable {
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
  // Open for testing.
  open var boundPort: Int? = null

  private lateinit var serverSocket: BoundDatagramSocket

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
  open fun start(udpSocketProvider: UdpSocketProvider) {
    logger.atFine().log("%s received start request!", this)
    if (threadIsActive) {
      logger.atFine().log("%s start request ignored: already running!", this)
      return
    }
    stopFlag = false
  }

  @Synchronized
  override fun stop() {
    stopFlag = true
    serverSocket.close()
  }

  @Synchronized
  protected open fun bind(udpSocketProvider: UdpSocketProvider, port: Int) {
    serverSocket =
      udpSocketProvider.bindSocket(
        io.ktor.network.sockets.InetSocketAddress("0.0.0.0", port),
        bufferSize
      )
    boundPort = port
    logger.atInfo().log("Accepting messages at %s", serverSocket.localAddress)
  }

  protected abstract fun allocateBuffer(): ByteBuffer

  protected abstract fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress)

  protected fun send(buffer: ByteBuffer, toSocketAddress: InetSocketAddress) {
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
      runBlocking {
        serverSocket.send(Datagram(ByteReadPacket(buffer), toSocketAddress.toKtorAddress()))
      }
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port %s", boundPort)
    }
  }

  override fun run() = runBlocking {
    threadIsActive = true
    logger.atFine().log("%s: thread running...", this)
    try {
      listeningOnPortsCounter.inc()
      while (!stopFlag) {
        try {
          val datagram: Datagram = serverSocket.receive()
          val buffer = datagram.packet.readByteBuffer()

          try {
            handleReceived(
              buffer,
              V086Utils.toJavaAddress(
                datagram.address as io.ktor.network.sockets.InetSocketAddress
              ),
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

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
