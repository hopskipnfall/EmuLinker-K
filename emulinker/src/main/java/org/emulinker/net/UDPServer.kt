package org.emulinker.net

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.Throws
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

abstract class UDPServer(
  shutdownOnExit: Boolean,
  metrics: MetricRegistry?,
  private val listeningOnPortsCounter: Counter
) : Executable {
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
  private var channel: DatagramChannel? = null

  final override var threadIsActive = false
    private set

  protected var stopFlag = false
    private set

  @get:Synchronized
  val isBound: Boolean
    get() {
      if (channel == null) return false
      return if (channel!!.socket() == null) false else !channel!!.socket().isClosed
    }
  val isConnected: Boolean
    get() = channel!!.isConnected

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
    if (channel != null) {
      try {
        channel!!.close()
      } catch (e: IOException) {
        logger.atSevere().withCause(e).log("Failed to close DatagramChannel")
      }
    }
  }

  @Synchronized
  @Throws(BindException::class)
  protected fun bind() {
    bind(-1)
  }

  @Synchronized
  @Throws(BindException::class)
  protected open fun bind(port: Int) {
    try {
      channel = DatagramChannel.open()
      if (port > 0) channel!!.socket().bind(InetSocketAddress(port))
      else channel!!.socket().bind(null)
      boundPort = channel!!.socket().localPort
      val tempBuffer = buffer
      val bufferSize = tempBuffer.capacity() * 2
      releaseBuffer(tempBuffer)
      channel!!.socket().receiveBufferSize = bufferSize
      channel!!.socket().sendBufferSize = bufferSize
    } catch (e: IOException) {
      throw BindException("Failed to bind to port $port", port, e)
    }
    start()
  }

  protected abstract val buffer: ByteBuffer

  protected abstract fun releaseBuffer(buffer: ByteBuffer)

  protected abstract fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress)

  protected fun send(buffer: ByteBuffer?, toSocketAddress: InetSocketAddress?) {
    if (!isBound) {
      logger
        .atWarning()
        .log(
          "Failed to send to %s: UDPServer is not bound!",
          formatSocketAddress(toSocketAddress!!)
        )
      return
    }
    /*
    if(artificalPacketLossPercentage > 0 && Math.abs(random.nextInt()%100) < artificalPacketLossPercentage)
    {
    	return;
    }
    */ try {
      //			logger.atFine().log("send("+EmuUtil.INSTANCE.dumpBuffer(buffer, false)+")");
      channel!!.send(buffer, toSocketAddress)
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port %s", boundPort)
    }
  }

  override fun run() {
    threadIsActive = true
    logger.atFine().log("%s: thread running...", this)
    try {
      listeningOnPortsCounter.inc()
      while (!stopFlag) {
        try {
          val buffer = buffer
          val fromSocketAddress = channel!!.receive(buffer)
          if (stopFlag) break
          if (fromSocketAddress == null)
            throw IOException("Failed to receive from DatagramChannel: fromSocketAddress == null")
          /*
          if(artificalPacketLossPercentage > 0 && Math.abs(random.nextInt()%100) < artificalPacketLossPercentage)
          {
          	releaseBuffer(buffer);
          	continue;
          }

          if(artificalDelay > 0)
          {
          	try
          	{
          		Thread.sleep(artificalDelay);
          	}
          	catch(Exception e) {}
          }
          */
          // Cast to avoid issue with java version mismatch:
          // https://stackoverflow.com/a/61267496/2875073
          (buffer as Buffer).flip()
          //					logger.atFine().log("receive("+EmuUtil.INSTANCE.dumpBuffer(buffer, false)+")");
          // TODO(nue): time this
          handleReceived(buffer, fromSocketAddress as InetSocketAddress)
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
