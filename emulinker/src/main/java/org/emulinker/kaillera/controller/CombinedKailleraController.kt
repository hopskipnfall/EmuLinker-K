package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.net.InetSocketAddress
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.*
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.util.EmuUtil.formatSocketAddress

class CombinedKailleraController(
  private val flags: RuntimeFlags,
  private val accessManager: AccessManager,
  kailleraServerController: KailleraServerController,
  private val server: KailleraServer,
) : SimpleChannelInboundHandler<DatagramPacket>() {
  private var boundPort: Int? = null

  private var stopFlag = false

  lateinit var nettyChannel: io.netty.channel.Channel
    private set

  @Synchronized
  fun stop() {
    if (stopFlag) return
    for (controller in controllersMap.values) {
      controller.stop()
    }
    stopFlag = true
  }

  @Synchronized
  fun bind(
    port: Int
  ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, port = port) {
        val group = NioEventLoopGroup(flags.nettyFlags)

        Runtime.getRuntime()
          .addShutdownHook(
            thread(start = false) {
              logger.atInfo().log("Received SIGTERM, shutting down gracefully.")
              try {
                server.announce("The server is shutting down", gamesAlso = true)
                Thread.sleep(1_000)

                for (handler in clientHandlers.values) {
                  server.quit(handler.user, "Server shutting down")
                }
                // Give the server time to notify everyone they are being kicked.
                Thread.sleep(1_000)
              } finally {
                nettyChannel.close()
              }
            }
          )

        try {
          Bootstrap().apply {
            group(group)
            channel(NioDatagramChannel::class.java)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.SO_BROADCAST, true)
            handler(this@CombinedKailleraController)

            nettyChannel = bind(port).sync().channel()
            nettyChannel.closeFuture().sync()
            boundPort = port
          }
        } finally {
          group.shutdownGracefully()
        }
      }
      .start(true)

  fun send(datagram: DatagramPacket) {
    try {
      this.nettyChannel.writeAndFlush(datagram)
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Failed to send on port %s", boundPort)
    }
  }

  /** Map of protocol name (e.g. "0.86") to [KailleraServerController]. */
  private val controllersMap = ConcurrentHashMap<String, KailleraServerController>()

  val clientHandlers = ConcurrentHashMap<InetSocketAddress, V086ClientHandler>()

  val bufferSize: Int = flags.v086BufferSize

  private fun handleReceived(
    buffer: ByteBuf,
    remoteSocketAddress: InetSocketAddress,
    ctx: ChannelHandlerContext,
  ) {
    var handler = clientHandlers[remoteSocketAddress]
    if (handler == null) {
      // User is new. It's either a ConnectMessage or it's the user's first message after
      // reconnecting to the server via the dictated port.
      val connectMessageResult: Result<ConnectMessage> = ConnectMessage.parse(buffer.nioBuffer())
      if (connectMessageResult.isSuccess) {
        when (val connectMessage = connectMessageResult.getOrThrow()) {
          is ConnectMessage_PING -> {
            val buf = nettyChannel.alloc().buffer(bufferSize)
            ConnectMessage_PONG().writeTo(buf)
            ctx.writeAndFlush(DatagramPacket(buf, remoteSocketAddress))
          }
          is RequestPrivateKailleraPortRequest -> {
            check(connectMessage.protocol == "0.83") {
              "Client listed unsupported protocol! $connectMessage."
            }

            val buf = nettyChannel.alloc().buffer(bufferSize)
            RequestPrivateKailleraPortResponse(flags.serverPort).writeTo(buf)
            ctx.writeAndFlush(DatagramPacket(buf, remoteSocketAddress))
          }
          else -> {
            logger
              .atWarning()
              .log(
                "Received unexpected message type from %s: %s",
                formatSocketAddress(remoteSocketAddress),
                connectMessageResult,
              )
          }
        }
        // We successfully parsed a connection message and handled it so return.
        return
      }

      // TODO(nue): I'm almost certain this can be removed?
      // The message should be parsed as a V086Message. Reset it.
      buffer.resetReaderIndex()

      if (!accessManager.isAddressAllowed(remoteSocketAddress.address)) {
        logger
          .atWarning()
          .log("AccessManager denied connection from %s", formatSocketAddress(remoteSocketAddress))
        return
      }

      handler =
        try {
          val protocolController: KailleraServerController = controllersMap.elements().nextElement()
          // TODO(nue): Don't hardcode this.
          protocolController.newConnection(
            remoteSocketAddress,
            "v086",
            this@CombinedKailleraController,
          )
        } catch (e: ServerFullException) {
          logger
            .atFine()
            .withCause(e)
            .log("Sending server full response to %s", formatSocketAddress(remoteSocketAddress))
          return
        } catch (e: NewConnectionException) {
          logger.atSevere().withCause(e).log("Couldn't create connection")
          return
        }

      clientHandlers[remoteSocketAddress] = handler!!
    }
    synchronized(handler.requestHandlerMutex) {
      handler.handleReceived(buffer, remoteSocketAddress)
    }
  }

  init {
    val clientTypes = kailleraServerController.clientTypes
    for (j in clientTypes.indices) {
      controllersMap[clientTypes[j]] = kailleraServerController
    }
  }

  override fun channelRead0(ctx: ChannelHandlerContext, packet: DatagramPacket) {
    handleReceived(packet.content().order(ByteOrder.LITTLE_ENDIAN), packet.sender(), ctx)
  }

  override fun channelRegistered(ctx: ChannelHandlerContext) {
    // This is only used by tests right now.
    if (!this::nettyChannel.isInitialized) this.nettyChannel = ctx.channel()

    logger.atInfo().log("Ready to accept connections on port")
    super.channelRegistered(ctx)
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
