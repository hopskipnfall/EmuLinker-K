package org.emulinker.net

import com.google.common.flogger.FluentLogger
import java.io.IOException
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.Runnable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.Executors
import kotlin.Throws
import org.emulinker.util.EmuUtil.formatSocketAddress

abstract class UDPRelay(
  var listenPort: Int,
  var serverSocketAddress: InetSocketAddress,
) : Runnable {
  var listenChannel: DatagramChannel? = null
    protected set
  protected var clients = Collections.synchronizedMap(HashMap<InetSocketAddress, ClientHandler>())
  protected abstract fun processClientToServer(
    receiveBuffer: ByteBuffer,
    fromAddress: InetSocketAddress,
    toAddress: InetSocketAddress
  ): ByteBuffer?

  protected abstract fun processServerToClient(
    receiveBuffer: ByteBuffer,
    fromAddress: InetSocketAddress,
    toAddress: InetSocketAddress
  ): ByteBuffer?

  override fun run() {
    logger.atInfo().log("Main port %d thread running...", listenPort)
    try {
      while (true) {
        val buffer = ByteBuffer.allocate(2048)
        val clientAddress = listenChannel!!.receive(buffer) as InetSocketAddress
        var clientHandler = clients[clientAddress]
        if (clientHandler == null) {
          clientHandler =
            try {
              ClientHandler(clientAddress)
            } catch (e: Exception) {
              logger
                .atSevere()
                .withCause(e)
                .log("Failed to start new ClientHandler for %s", formatSocketAddress(clientAddress))
              continue
            }
          clients[clientAddress] = clientHandler
          threadPool.execute(clientHandler)
        }

        // Cast to avoid issue with java version mismatch:
        // https://stackoverflow.com/a/61267496/2875073
        (buffer as Buffer).flip()
        clientHandler?.send(buffer)
      }
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Main port %d thread caught exception", listenPort)
    } finally {
      try {
        listenChannel!!.close()
      } catch (e: Exception) {}
      threadPool.shutdownNow()
    }
    logger.atInfo().log("Main port %d thread exiting...", listenPort)
  }

  protected inner class ClientHandler(protected var clientSocketAddress: InetSocketAddress) :
    Runnable {
    protected var clientChannel: DatagramChannel
    @Throws(Exception::class)
    fun send(buffer: ByteBuffer) {
      //			logger.atInfo().log(EmuUtil.formatSocketAddress(clientSocketAddress) + " -> \t" +
      // EmuUtil.INSTANCE.dumpBuffer(buffer));
      val newBuffer = processClientToServer(buffer, clientSocketAddress, serverSocketAddress)
      clientChannel.send(newBuffer, serverSocketAddress)
    }

    override fun run() {
      logger
        .atInfo()
        .log("ClientHandler thread for %s runnning...", formatSocketAddress(clientSocketAddress))
      try {
        while (true) {
          val buffer = ByteBuffer.allocate(2048)
          val receiveAddress = clientChannel.receive(buffer) as InetSocketAddress
          if (receiveAddress.address != serverSocketAddress.address) continue

          // Cast to avoid issue with java version mismatch:
          // https://stackoverflow.com/a/61267496/2875073
          (buffer as Buffer).flip()

          //					logger.atInfo().log(EmuUtil.formatSocketAddress(clientSocketAddress) + " <- \t" +
          // EmuUtil.INSTANCE.dumpBuffer(buffer));
          val newBuffer = processServerToClient(buffer, receiveAddress, clientSocketAddress)
          listenChannel!!.send(newBuffer, clientSocketAddress)
        }
      } catch (e: Exception) {
        logger
          .atInfo()
          .withCause(e)
          .log(
            "ClientHandler thread for " +
              formatSocketAddress(clientSocketAddress) +
              " caught exception"
          )
      } finally {
        try {
          clientChannel.close()
        } catch (e: Exception) {}
        clients.remove(clientSocketAddress)
      }
      logger
        .atInfo()
        .log("ClientHandler thread for %s exiting...", formatSocketAddress(clientSocketAddress))
    }

    init {
      clientChannel = DatagramChannel.open()
      clientChannel.socket().bind(null)
      logger
        .atInfo()
        .log(
          "ClientHandler for %s bound to port %d",
          formatSocketAddress(clientSocketAddress),
          clientChannel.socket().port
        )
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    protected var threadPool = Executors.newCachedThreadPool()
  }

  init {
    try {
      listenChannel = DatagramChannel.open()
      listenChannel!!.socket().bind(InetSocketAddress(InetAddress.getLocalHost(), listenPort))
    } catch (e: IOException) {
      logger.atSevere().withCause(e).log("Failed to bind to channel")
      throw IllegalStateException(e)
    }
    logger.atInfo().log("Bound to port %d", listenPort)
    threadPool.execute(this)
  }
}
