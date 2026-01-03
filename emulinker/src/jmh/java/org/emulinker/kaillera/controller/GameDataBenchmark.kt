package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.AllReady
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.CreateGameNotification
import org.emulinker.kaillera.controller.v086.protocol.CreateGameRequest
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.controller.v086.protocol.JoinGameNotification
import org.emulinker.kaillera.controller.v086.protocol.JoinGameRequest
import org.emulinker.kaillera.controller.v086.protocol.QuitNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitRequest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.StartGameNotification
import org.emulinker.kaillera.controller.v086.protocol.StartGameRequest
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.koinModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
open class GameDataBenchmark {

  @Benchmark
  fun benchmark1Player(state: State1P, blackhole: Blackhole) {
    runBenchmark(state, blackhole)
  }

  @Benchmark
  fun benchmark2Players(state: State2P, blackhole: Blackhole) {
    runBenchmark(state, blackhole)
  }

  @Benchmark
  fun benchmark3Players(state: State3P, blackhole: Blackhole) {
    runBenchmark(state, blackhole)
  }

  @Benchmark
  fun benchmark4Players(state: State4P, blackhole: Blackhole) {
    runBenchmark(state, blackhole)
  }

  private fun runBenchmark(state: GameState, blackhole: Blackhole) {
    state.clients.forEach { it.sendGameData() }
    state.clients.forEach { it.receiveGameData(blackhole) }

    state.succeeded++
  }

  @State(Scope.Benchmark)
  abstract class GameState : KoinComponent {
    abstract val playerCount: Int

    lateinit var channel: EmbeddedChannel
    lateinit var controller: CombinedKailleraController
    lateinit var userActionsExecutor: ThreadPoolExecutor

    var succeeded = 0L

    val clientQueues = ConcurrentHashMap<Int, BlockingQueue<OutgoingMsg>>()
    lateinit var clients: List<Client>
    lateinit var clientMap: Map<Int, Client>

    @Setup(Level.Trial)
    fun setup() {
      startKoin {
        allowOverride(true)
        modules(koinModule, ActionModule, module { single<AccessManager> { FakeAccessManager() } })
      }
      controller = get()
      val kailleraServerController: KailleraServerController = get()
      kailleraServerController.start()
      userActionsExecutor = get(named("userActionsExecutor"))
      val adapter = ControllerAdapter(controller)
      channel = EmbeddedChannel(adapter)
      // Inject channel back into controller so it can send responses
      controller.nettyChannel = channel

      val _clients = ArrayList<Client>()
      val _clientMap = ConcurrentHashMap<Int, Client>()

      for (i in 1..playerCount) {
        val port = i * 1111
        val client = Client(port, "User$i", this)
        _clients.add(client)
        _clientMap[port] = client

        // Advance client iterator to desync for subsequent clients
        if (i > 1) {
          repeat(1000) { client.gameDataIterator.next() }
        }
      }
      clients = _clients
      clientMap = _clientMap

      logger.atInfo().log("Starting setup for $playerCount players...")

      clients.forEach {
        it.login()
        Thread.sleep(100)
      }

      val creator = clients[0]
      val gameId = creator.createGame()

      // Others wait for creation notification and join
      for (i in 1 until clients.size) {
        clients[i].consumeUntil { it is CreateGameNotification && it.gameId == gameId }
        clients[i].joinGame(gameId)
      }

      // Creator waits for everyone to join
      repeat(clients.size - 1) {
        creator.consumeUntil { it is JoinGameNotification && it.gameId == gameId }
      }

      creator.startGame()

      // Others wait for Start
      for (i in 1 until clients.size) {
        clients[i].consumeUntil { it is StartGameNotification }
      }

      clients.forEach { it.sendReady() }
      clients.forEach { it.waitForReady() }

      logger.atInfo().log("Setup complete.")
    }

    @TearDown(Level.Trial)
    fun teardown() {
      try {
        clients.forEach { it.logout() }
      } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Failed to logout cleanly")
      }

      channel.close()
      controller.stop()
      userActionsExecutor.shutdown()
      stopKoin()
    }

    fun pump() {
      channel.runPendingTasks()
      while (true) {
        val response: DatagramPacket = channel.readOutbound<DatagramPacket>() ?: break

        val port = response.recipient().port
        val queue = clientQueues[port]
        if (queue == null) {
          logger.atWarning().log("Received message for unknown port: %d", port)
          response.release()
          continue
        }

        val content = response.content()
        val connectMessage = ConnectMessage.parse(content.nioBuffer())
        if (connectMessage.isSuccess) {
          queue.add(
            OutgoingMsg.ConnectionMessage(
              connectMessage.getOrThrow() as RequestPrivateKailleraPortResponse
            )
          )
          response.release()
          continue
        }

        content.resetReaderIndex()
        val client = clientMap[port]
        if (client == null) {
          logger.atWarning().log("Client not found for port: %d", port)
          response.release()
          continue
        }

        val lastId = client.lastMessageNumberReceived

        try {
          val bundle = V086Bundle.parse(content, lastMessageID = lastId)
          queue.add(OutgoingMsg.Bundle(bundle))
        } catch (e: Exception) {
          logger.atSevere().withCause(e).log("Failed to parse bundle for port %d", port)
        } finally {
          response.release()
        }
      }
    }

    class ControllerAdapter(private val controller: CombinedKailleraController) :
      SimpleChannelInboundHandler<DatagramPacket>() {
      override fun channelRead0(ctx: ChannelHandlerContext, packet: DatagramPacket) {
        controller.handleReceived(
          packet.content().order(ByteOrder.LITTLE_ENDIAN),
          packet.sender(),
          ctx,
        )
      }
    }
  }

  // Concrete state classes
  open class State1P : GameState() {
    override val playerCount = 1
  }

  open class State2P : GameState() {
    override val playerCount = 2
  }

  open class State3P : GameState() {
    override val playerCount = 3
  }

  open class State4P : GameState() {
    override val playerCount = 4
  }

  sealed interface OutgoingMsg {
    data class Bundle(val message: V086Bundle) : OutgoingMsg

    data class ConnectionMessage(val message: RequestPrivateKailleraPortResponse) : OutgoingMsg
  }

  class Client(val port: Int, val username: String, val gameState: GameState) {
    var lastMessageNumberReceived = -1
    var lastMessageNumber = -1
    val sender = InetSocketAddress("127.0.0.1", port)
    val gameDataIterator = buildIterator()

    private val queue = ArrayBlockingQueue<OutgoingMsg>(100)

    init {
      gameState.clientQueues[port] = queue
    }

    // Helper to yield messages from the queue
    private val messageIterator = iterator {
      while (true) {
        gameState.pump()

        while (true) {
          val msg = queue.poll() ?: break
          when (msg) {
            is OutgoingMsg.Bundle -> {
              val bundle = msg.message
              for (m in bundle.messages.filterNotNull().sortedBy { it.messageNumber }) {
                yield(m.also { lastMessageNumberReceived = it.messageNumber })
              }
            }

            is OutgoingMsg.ConnectionMessage -> {
              // ignored in this iterator
            }
          }
        }

        yield(null)
      }
    }

    fun login() {
      val request = RequestPrivateKailleraPortRequest("0.83")
      val buffer =
        Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
      gameState.channel.writeInbound(DatagramPacket(buffer, RECIPIENT, sender))

      var loggedIn = false
      while (!loggedIn) {
        gameState.pump()
        val msg = queue.poll()
        if (msg is OutgoingMsg.ConnectionMessage) {
          loggedIn = true
        } else if (msg != null) {
          // ignore
        } else {
          Thread.yield()
        }
      }

      sendBundle(
        singleBundle(
          UserInformation(
            messageNumber = ++lastMessageNumber,
            username = username,
            clientType = "Test Client",
            connectionType = ConnectionType.LAN,
          )
        )
      )

      consumeUntil {
        if (it is ServerAck) {
          sendBundle(singleBundle(ClientAck(++lastMessageNumber)))
          false
        } else {
          it is UserJoined && it.username == this.username
        }
      }
    }

    fun createGame(): Int {
      sendBundle(singleBundle(CreateGameRequest(++lastMessageNumber, "Test Game")))
      var gameId = -1
      consumeUntil {
        if (it is CreateGameNotification && it.username == this.username) {
          gameId = it.gameId
          true
        } else false
      }
      return gameId
    }

    fun joinGame(gameId: Int) {
      sendBundle(singleBundle(JoinGameRequest(++lastMessageNumber, gameId, ConnectionType.LAN)))
      consumeUntil { it is JoinGameNotification && it.username == this.username }
    }

    fun startGame() {
      sendBundle(singleBundle(StartGameRequest(++lastMessageNumber)))
      consumeUntil { it is StartGameNotification }
    }

    fun sendReady() {
      sendBundle(singleBundle(AllReady(++lastMessageNumber)))
    }

    fun waitForReady() {
      consumeUntil { it is AllReady }
    }

    fun logout() {
      sendBundle(singleBundle(QuitRequest(++lastMessageNumber, "benchmark done")))
      consumeUntil { it is QuitNotification && it.username == this.username }
    }

    fun sendGameData() {
      sendBundle(singleBundle(GameData(++lastMessageNumber, gameDataIterator.next())))
    }

    fun receiveGameData(blackhole: Blackhole) {
      var received = false
      while (!received) {
        val msg = nextMessage()
        if (msg == null) {
          Thread.yield()
          continue
        }
        try {
          if (msg is GameData || msg is CachedGameData) {
            received = true
            blackhole.consume(msg)
          }
        } finally {
          io.netty.util.ReferenceCountUtil.release(msg)
        }
      }
    }

    private fun nextMessage(): V086Message? {
      return messageIterator.next() as? V086Message
    }

    fun consumeUntil(predicate: (V086Message) -> Boolean) {
      while (true) {
        val msg =
          nextMessage()
            ?: run {
              Thread.yield()
              null
            }
        if (msg != null) {
          try {
            if (predicate(msg)) return
          } finally {
            io.netty.util.ReferenceCountUtil.release(msg)
          }
        }
      }
    }

    fun sendBundle(bundle: V086Bundle) {
      val buffer =
        Unpooled.buffer(1024).order(ByteOrder.LITTLE_ENDIAN).apply { bundle.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, sender)
      gameState.channel.writeInbound(packet)
    }

    private fun singleBundle(message: V086Message): V086Bundle {
      return V086Bundle(arrayOf(message))
    }
  }

  private companion object {
    init {
      AppModule.charsetDoNotUse = Charsets.UTF_8
    }

    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)

    val logger = FluentLogger.forEnclosingClass()

    val LINES: List<String> by lazy {
      val inputStream =
        GameDataBenchmark::class.java.getResourceAsStream("/ssb_p1_out.txt")
          ?: throw java.io.FileNotFoundException("ssb_p1_out.txt not found in classpath")
      inputStream.bufferedReader().readLines()
    }

    private fun buildIterator() =
      iterator<ByteArray> {
        lateinit var previousLine: String
        while (true) {
          for (line in LINES) {
            if (line.startsWith("x")) {
              val times = line.removePrefix("x").toInt()
              repeat(times) { yield(previousLine.decodeHex()) }
            } else {
              yield(line.decodeHex())
            }
            previousLine = line
          }
        }
      }
  }

  class FakeAccessManager : AccessManager {
    override fun isAddressAllowed(address: InetAddress): Boolean = true

    override fun isSilenced(address: InetAddress): Boolean = false

    override fun isEmulatorAllowed(emulator: String?): Boolean = true

    override fun isGameAllowed(game: String): Boolean = true

    override fun getAccess(address: InetAddress): Int = AccessManager.ACCESS_ADMIN

    override fun getAnnouncement(address: InetAddress): String? = null

    override fun addTempBan(addressPattern: String, duration: Duration) {}

    override fun addTempAdmin(addressPattern: String, duration: Duration) {}

    override fun addTempModerator(addressPattern: String, duration: Duration) {}

    override fun addTempElevated(addressPattern: String, duration: Duration) {}

    override fun addSilenced(addressPattern: String, duration: Duration) {}

    override fun clearTemp(address: InetAddress, clearAll: Boolean): Boolean = false

    override fun close() {}
  }
}

private fun String.decodeHex(): ByteArray {
  check(length % 2 == 0) { "Must have an even length" }
  return chunked(2).map { it.lowercase().toInt(16).toByte() }.toByteArray()
}
