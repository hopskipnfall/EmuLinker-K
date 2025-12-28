package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
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
import org.emulinker.util.VariableSizeByteArray
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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1)
open class GameDataBenchmark : KoinComponent {
  lateinit var channel: EmbeddedChannel
  lateinit var controller: CombinedKailleraController
  lateinit var userActionsExecutor: ThreadPoolExecutor

  var succeeded = 0L

  private var port = 0
  private var lastMessageNumberReceived = -1
  private var lastMessageNumber = -1
  private lateinit var sender: InetSocketAddress
  private lateinit var messageIterator: Iterator<V086Message?>

  @Setup(Level.Trial)
  fun setup() {
    startKoin {
      allowOverride(true)
      modules(koinModule, ActionModule, module { single<AccessManager> { FakeAccessManager() } })
    }
    controller = get()
    userActionsExecutor = get(named("userActionsExecutor"))
    channel = EmbeddedChannel(controller)

    lastMessageNumberReceived = -1
    lastMessageNumber = -1
    port++
    sender = InetSocketAddress("127.0.0.1", port)
    logger.atInfo().log("Starting trial on port %d", port)

    createMessageIterator()

    login()
    createGame()
    startGame()
    readyCheck()
  }

  @TearDown(Level.Trial)
  fun teardown() {
    try {
      logout()
    } catch (e: Exception) {
      logger.atWarning().withCause(e).log("Failed to logout cleanly")
    }

    channel.close()
    controller.stop()
    userActionsExecutor.shutdown()
    stopKoin()
  }

  private fun createMessageIterator() {
    messageIterator = iterator {
      while (true) {
        when (val message = readOutgoing()) {
          is OutgoingMessage.Bundle -> {
            when (val msg = message.message) {
              is V086Bundle.Single ->
                yield(msg.message.also { lastMessageNumberReceived = it.messageNumber })

              is V086Bundle.Multi -> {
                for (m in msg.messages.filterNotNull().sortedBy { it.messageNumber }) {
                  yield(m.also { lastMessageNumberReceived = it.messageNumber })
                }
              }
            }
          }

          is OutgoingMessage.ConnectionMessage ->
            throw IllegalStateException("Unexpected Connection message in stream")

          OutgoingMessage.Null -> {
            yield(null) // Yield null to indicate nothing read yet
          }
        }
      }
    }
  }

  private fun nextMessage(): V086Message {
    while (true) {
      val msg = messageIterator.next()
      if (msg != null) return msg
      Thread.yield()
      if (Thread.interrupted()) throw InterruptedException()
    }
  }

  private fun login() {
    // 1. Request port
    val request = RequestPrivateKailleraPortRequest("0.83")
    val buffer = Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
    channel.writeInbound(DatagramPacket(buffer, RECIPIENT, sender))

    // 2. Wait for response
    while (true) {
      when (val message = readOutgoing()) {
        is OutgoingMessage.Bundle ->
          throw IllegalStateException("Received bundle before being logged in")

        is OutgoingMessage.ConnectionMessage -> break // Got it
        OutgoingMessage.Null -> {
          Thread.yield()
          if (Thread.interrupted()) throw InterruptedException()
        }
      }
    }

    // 3. Send User Info
    sendBundle(
      V086Bundle.Single(
        UserInformation(
          messageNumber = ++lastMessageNumber,
          username = "testUser$port",
          clientType = "Test Client",
          connectionType = ConnectionType.LAN,
        )
      )
    )

    // 4. Wait for UserJoined (handling ServerAck)
    var joined = false
    while (!joined) {
      when (val msg = nextMessage()) {
        is ServerAck -> sendBundle(V086Bundle.Single(ClientAck(++lastMessageNumber)))
        is UserJoined -> if (msg.username == "testUser$port") joined = true
        else -> {}
      }
    }
  }

  private fun createGame() {
    sendBundle(V086Bundle.Single(CreateGameRequest(++lastMessageNumber, "Test Game")))
    var created = false
    while (!created) {
      val msg = nextMessage()
      if (msg is CreateGameNotification && msg.username == "testUser$port") {
        created = true
      }
    }
  }

  private fun startGame() {
    sendBundle(V086Bundle.Single(StartGameRequest(++lastMessageNumber)))
    var started = false
    while (!started) {
      if (nextMessage() is StartGameNotification) started = true
    }
  }

  private fun readyCheck() {
    sendBundle(V086Bundle.Single(AllReady(++lastMessageNumber)))
    var ready = false
    while (!ready) {
      if (nextMessage() is AllReady) ready = true
    }
  }

  private fun logout() {
    sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, "benchmark done")))
    var quit = false
    while (!quit) {
      val msg = nextMessage()
      if (msg is QuitNotification && msg.username == "testUser$port") {
        quit = true
      }
      // If we see GameData here, we might want to ignore it or consume it, but ideally we shouldn't
      // receive much else.
    }
  }

  @Benchmark
  fun benchmark(blackhole: Blackhole) {
    val data = VariableSizeByteArray(byteArrayOf(1, 2, 3, 4))
    sendBundle(V086Bundle.Single(GameData(++lastMessageNumber, data)))

    var received = false
    while (!received) {
      val msg = messageIterator.next()
      if (msg == null) {
        Thread.yield()
        if (Thread.interrupted()) throw InterruptedException()
        continue
      }
      if (msg is GameData || msg is CachedGameData) {
        received = true
        blackhole.consume(msg)
      }
    }
    succeeded++
  }

  fun sendBundle(bundle: V086Bundle) {
    val buffer = Unpooled.buffer(1024).apply { bundle.writeTo(this) }
    val packet = DatagramPacket(buffer, RECIPIENT, sender)
    channel.writeInbound(packet)
  }

  sealed interface OutgoingMessage {
    object Null : OutgoingMessage

    @JvmInline value class Bundle(val message: V086Bundle) : OutgoingMessage

    @JvmInline
    value class ConnectionMessage(val message: RequestPrivateKailleraPortResponse) :
      OutgoingMessage
  }

  fun readOutgoing(): OutgoingMessage {
    channel.runPendingTasks()
    val response: DatagramPacket =
      channel.readOutbound<DatagramPacket>() ?: return OutgoingMessage.Null
    try {
      if (response.recipient().port != port) {
        logger.atInfo().log("INCORRECT PORT: %d", response.recipient().port)
        return OutgoingMessage.Null
      }
      val message = ConnectMessage.parse(response.content())
      if (message.isSuccess) {
        return OutgoingMessage.ConnectionMessage(
          message.getOrThrow() as RequestPrivateKailleraPortResponse
        )
      }
      val content = response.content()
      return try {
        OutgoingMessage.Bundle(V086Bundle.parse(content, lastMessageID = lastMessageNumberReceived))
      } catch (e: Exception) {
        logger.atSevere().withCause(e).log("Failed to parse bundle")
        OutgoingMessage.Null
      }
    } finally {
      response.release()
    }
  }

  private companion object {
    init {
      AppModule.charsetDoNotUse = Charsets.UTF_8
    }

    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)

    val logger = FluentLogger.forEnclosingClass()
  }

  class FakeAccessManager : AccessManager {
    override fun isAddressAllowed(address: InetAddress): Boolean = true

    override fun isSilenced(address: InetAddress): Boolean = false

    override fun isEmulatorAllowed(emulator: String): Boolean = true

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
