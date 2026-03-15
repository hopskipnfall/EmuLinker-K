package org.emulinker.kaillera.controller

import com.google.common.truth.Expect
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic
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
import org.emulinker.kaillera.controller.v086.protocol.GameChatRequest
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.controller.v086.protocol.JoinGameNotification
import org.emulinker.kaillera.controller.v086.protocol.JoinGameRequest
import org.emulinker.kaillera.controller.v086.protocol.QuitGameRequest
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
import org.emulinker.util.FastGameDataCache
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

abstract class E2ETestBase : KoinComponent {

  lateinit var channel: EmbeddedChannel
  lateinit var controller: CombinedKailleraController
  lateinit var userActionsExecutor: ThreadPoolExecutor

  val clientQueues = ConcurrentHashMap<Int, BlockingQueue<OutgoingMsg>>()
  val clientMap = ConcurrentHashMap<Int, Client>()

  open class FakeAccessManager : AccessManager {
    val shadowBans = mutableSetOf<String>()
    val accessLevels = mutableMapOf<String, Int>()

    override fun isAddressAllowed(address: InetAddress): Boolean = true

    override fun isSilenced(address: InetAddress): Boolean = false

    override fun isEmulatorAllowed(emulator: String): Boolean = true

    override fun isGameAllowed(game: String): Boolean = true

    override fun getAccess(address: InetAddress): Int =
      accessLevels[address.hostAddress] ?: AccessManager.ACCESS_ADMIN

    override fun getAnnouncement(address: InetAddress): String? = null

    override fun addTempBan(addressPattern: String, duration: Duration) {}

    override fun addTempAdmin(addressPattern: String, duration: Duration) {}

    override fun addTempModerator(addressPattern: String, duration: Duration) {}

    override fun addTempElevated(addressPattern: String, duration: Duration) {}

    override fun addSilenced(addressPattern: String, duration: Duration) {}

    override fun isShadowBanned(address: InetAddress): Boolean =
      shadowBans.contains(address.hostAddress)

    override fun addShadowBan(addressPattern: String) {
      shadowBans.add(addressPattern)
    }

    override fun removeShadowBan(addressPattern: String): Boolean =
      shadowBans.remove(addressPattern)

    override fun clearTemp(address: InetAddress, clearAll: Boolean): Boolean = false

    override fun close() {}
  }

  open fun createAccessManager(): AccessManager = FakeAccessManager()

  @Before
  open fun setup() {
    AppModule.charsetDoNotUse = Charsets.UTF_8
    startKoin {
      allowOverride(true)
      modules(koinModule, ActionModule, module { single<AccessManager> { createAccessManager() } })
    }
    controller = get()
    userActionsExecutor = get(named("userActionsExecutor"))
    channel = EmbeddedChannel(controller)
  }

  @After
  open fun teardown() {
    try {
      clientMap.values.forEach { it.logout() }
    } catch (e: Exception) {
      // ignore
    }

    if (::channel.isInitialized) {
      channel.close()
    }
    if (::controller.isInitialized) {
      controller.stop()
    }
    if (::userActionsExecutor.isInitialized) {
      userActionsExecutor.shutdown()
    }
    stopKoin()
  }

  @Rule @JvmField val expect = Expect.create()

  fun pump() {
    channel.runPendingTasks()
    val started = Monotonic.markNow()
    while (true) {
      if (started.elapsedNow() > 1.seconds) {
        throw IllegalStateException("Timed out pumping")
      }

      val response: DatagramPacket = channel.readOutbound<DatagramPacket>() ?: break
      val port = response.recipient().port
      val queue = clientQueues[port]
      if (queue == null) {
        response.release()
        continue
      }

      val content = response.content()
      val connectMessage = ConnectMessage.parse(content)
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
        response.release()
        continue
      }

      val lastId = client.lastMessageNumberReceived
      try {
        val bundle = V086Bundle.parse(content, lastMessageID = lastId)
        queue.add(OutgoingMsg.Bundle(bundle))
      } catch (e: Exception) {
        println("Exception parsing bundle: \${e.message}")
        e.printStackTrace()
      } finally {
        response.release()
      }
    }
  }

  sealed interface OutgoingMsg {
    data class Bundle(val message: V086Bundle) : OutgoingMsg

    data class ConnectionMessage(val message: RequestPrivateKailleraPortResponse) : OutgoingMsg
  }

  class Client(
    port: Int,
    val username: String,
    val testInstance: E2ETestBase,
    val useOutgoingCache: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.LAN,
    val loginDelay: Duration = Duration.ZERO,
  ) {
    var lastMessageNumberReceived = -1
    var lastMessageNumber = -1
    val sender =
      InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, port.toByte())), port)
    private val queue = ArrayBlockingQueue<OutgoingMsg>(100)
    private val cache = ArrayList<ByteBuf>()
    private val outgoingCache = FastGameDataCache(256)

    private val inputIterator = buildIterator()

    init {
      testInstance.clientQueues[port] = queue
      testInstance.clientMap[port] = this
    }

    fun nextInput(): ByteBuf = inputIterator.next()

    fun advanceIterator(count: Int) {
      repeat(count) { inputIterator.next() }
    }

    private val messageIterator = iterator {
      // We removed the strict timeout check here because it breaks consumeUntil's
      // built-in timeout mechanism by throwing early during normal pump loops.
      while (true) {
        testInstance.pump()

        while (true) {
          val msg = queue.poll() ?: break
          when (msg) {
            is OutgoingMsg.Bundle -> {
              when (val inner = msg.message) {
                is V086Bundle.Single ->
                  yield(inner.message.also { lastMessageNumberReceived = it.messageNumber })

                is V086Bundle.Multi -> {
                  for (m in inner.messages.filterNotNull().sortedBy { it.messageNumber }) {
                    yield(m.also { lastMessageNumberReceived = it.messageNumber })
                  }
                }
              }
            }
            is OutgoingMsg.ConnectionMessage -> {}
          }
        }
        yield(null)
      }
    }

    fun login() {
      val request = RequestPrivateKailleraPortRequest("0.83")
      val buffer =
        Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
      testInstance.channel.writeInbound(DatagramPacket(buffer, RECIPIENT, sender))

      var loggedIn = false
      while (!loggedIn) {
        testInstance.pump()
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
        V086Bundle.Single(
          UserInformation(++lastMessageNumber, username, "Test Client", connectionType)
        )
      )
      consumeUntil {
        if (it is ServerAck) {
          sendBundle(V086Bundle.Single(ClientAck(++lastMessageNumber)))
          false
        } else {
          it is UserJoined && it.username == this.username
        }
      }
    }

    fun createGame(): Int {
      sendBundle(V086Bundle.Single(CreateGameRequest(++lastMessageNumber, "Test Game")))
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
      sendBundle(V086Bundle.Single(JoinGameRequest(++lastMessageNumber, gameId, connectionType)))
      consumeUntil {
        it is JoinGameNotification && it.username == this.username && it.gameId == gameId
      }
    }

    fun startGame() {
      sendBundle(V086Bundle.Single(StartGameRequest(++lastMessageNumber)))
      consumeUntil { it is StartGameNotification }
    }

    fun waitForGameStarted() {
      consumeUntil { it is StartGameNotification }
    }

    fun sendReady() {
      sendBundle(V086Bundle.Single(AllReady(++lastMessageNumber)))
    }

    fun waitForReady() {
      consumeUntil { it is AllReady }
    }

    fun sendGameData(data: ByteBuf) {
      if (useOutgoingCache) {
        val index = outgoingCache.indexOf(data)
        if (index != -1) {
          sendBundle(V086Bundle.Single(CachedGameData(++lastMessageNumber, index)))
        } else {
          outgoingCache.add(data)
          sendBundle(V086Bundle.Single(GameData(++lastMessageNumber, data)))
        }
      } else {
        sendBundle(V086Bundle.Single(GameData(++lastMessageNumber, data)))
      }
    }

    fun receiveGameData(timeout: Duration = 1.seconds): ByteBuf {
      val deadline = System.nanoTime() + timeout.inWholeNanoseconds
      while (System.nanoTime() < deadline) {
        val msg = nextMessage()
        if (msg == null) {
          Thread.yield()
          continue
        }
        if (msg is GameData) {
          if (cache.size >= 256) {
            cache.removeAt(0)
          }
          cache.add(msg.gameData)
          return msg.gameData
        }
        if (msg is CachedGameData) {
          return cache[msg.key]
        }
      }
      throw RuntimeException("Timed out waiting for GameData")
    }

    fun sendChat(message: String) {
      sendBundle(V086Bundle.Single(GameChatRequest(++lastMessageNumber, message)))
    }

    fun quitGame() {
      sendBundle(V086Bundle.Single(QuitGameRequest(++lastMessageNumber)))
    }

    fun quit() {
      sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, "Test Client Shutdown")))
    }

    fun logout() {
      sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, "test done")))
      try {
        consumeUntil { it is QuitNotification && it.username == this.username }
      } catch (e: Exception) {}
    }

    fun nextMessage(): V086Message? = messageIterator.next()

    fun consumeUntil(timeoutMs: Long = 5000, predicate: (V086Message) -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        val msg =
          nextMessage()
            ?: run {
              Thread.yield()
              null
            }
        if (msg != null) {
          println("[${this.username}] Received msg: $msg")
          if (predicate(msg)) return
        }
      }
      throw RuntimeException("Timeout waiting for message")
    }

    fun verifyNoMessage(timeoutMs: Long = 500, predicate: (V086Message) -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        val msg =
          nextMessage()
            ?: run {
              Thread.yield()
              null
            }
        if (msg != null && predicate(msg)) {
          throw IllegalStateException("Received unexpected message matching predicate")
        }
      }
      // Success if we timed out
    }

    fun sendBundle(bundle: V086Bundle) {
      val buffer = Unpooled.buffer(1024).apply { bundle.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, sender)
      testInstance.channel.writeInbound(packet)
    }

    fun buildIterator(): Iterator<ByteBuf> = iterator {
      var counter = 0
      while (true) {
        val buf = Unpooled.buffer(4)
        buf.writeInt(counter++ xor username.hashCode())
        yield(buf)
      }
    }
  }

  companion object {
    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)
  }
}

fun ByteBuf.toByteArray(): ByteArray {
  val arr = ByteArray(readableBytes())
  getBytes(readerIndex(), arr)
  return arr
}
