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
import kotlin.Throws
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

abstract class UDPServer(shutdownOnExit: Boolean, private val listeningOnPortsCounter: Counter) :
  Executable {
  open var boundPort: Int? = null

  final override var threadIsActive = false
    private set

  protected var stopFlag = false
    private set

  private lateinit var serverSocket: BoundDatagramSocket
  abstract val bufferSize: Int
  abstract val dispatcher: CoroutineContext

  @get:Synchronized
  val isBound: Boolean
    get() = !serverSocket.isClosed

  @Synchronized
  open fun start() {
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
  @Throws(BindException::class)
  protected open fun bind(port: Int, udpSocketProvider: UdpSocketProvider) {
    serverSocket =
      udpSocketProvider.bindSocket(
        io.ktor.network.sockets.InetSocketAddress("0.0.0.0", port),
        bufferSize
      )
    boundPort = port
  }

  protected abstract fun allocateIncomingBuffer(): ByteBuffer

  protected abstract fun releaseBuffer(buffer: ByteBuffer)

  protected abstract fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress)

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

  private inner class ShutdownThread : Thread() {
    override fun run() {
      this@UDPServer.stop()
    }
  }

  init {
    if (shutdownOnExit) Runtime.getRuntime().addShutdownHook(ShutdownThread())
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
