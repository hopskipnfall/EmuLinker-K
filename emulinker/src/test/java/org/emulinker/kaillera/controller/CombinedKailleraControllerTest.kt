package org.emulinker.kaillera.controller

import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteOrder
import kotlin.test.assertNotNull
import kotlin.time.Duration
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PING
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PONG
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.CreateGameNotification
import org.emulinker.kaillera.controller.v086.protocol.CreateGameRequest
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.controller.v086.protocol.GameStatus
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.JoinGameNotification
import org.emulinker.kaillera.controller.v086.protocol.JoinGameRequest
import org.emulinker.kaillera.controller.v086.protocol.PlayerInformation
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.ServerStatus
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType.LAN
import org.emulinker.kaillera.model.GameStatus.WAITING
import org.emulinker.kaillera.model.UserStatus
import org.emulinker.kaillera.pico.koinModule
import org.emulinker.kaillera.release.ReleaseInfo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class CombinedKailleraControllerTest : ProtocolBaseTest(), KoinTest {
  @get:Rule val koinTestRule = KoinTestRule.create { modules(koinModule, ActionModule) }

  @get:Rule val expect = Expect.create()

  private val server: CombinedKailleraController by inject()

  private val releaseInfo: ReleaseInfo by inject()

  private lateinit var channel: EmbeddedChannel

  @Test
  fun `test direct packet injection`() {
    val sender = InetSocketAddress("127.0.0.1", 12345)
    val recipient = InetSocketAddress("127.0.0.1", 27888)

    // Create a PING packet
    val buffer = Unpooled.buffer()
    ConnectMessage_PING.writeTo(buffer)
    val packet = DatagramPacket(buffer, recipient, sender)

    // Write inbound
    channel.writeInbound(packet)

    val response: DatagramPacket = assertNotNull(channel.readOutbound<DatagramPacket>())
    expect
      .that(ConnectMessage.parse(response.content()))
      .isEqualTo(Result.success(ConnectMessage_PONG))
    expect.that(response.recipient().address.hostAddress).isEqualTo("127.0.0.1")
    expect.that(response.recipient().port).isEqualTo(12345)
  }

  @Before
  fun before() {
    channel =
      EmbeddedChannel(
        server
      )
  }

  @After
  fun after() {
    // Make sure we didn't skip any messages.
    retrieveMessages(Int.MAX_VALUE)
    for ((port, messages) in portToMessages) {
      expect.withMessage("Port $port should have no outstanding messages").that(messages).isEmpty()
    }
    expect.that(channel.inboundMessages()).isEmpty()

    channel.close()
  }

  @Test
  fun `log into server and create a game`() {
    requestPort(clientPort = 1)

    login(clientPort = 1, existingUsers = emptyList(), existingGames = emptyList())

    createGame(clientPort = 1)
  }

  @Test
  fun `two users in server and join game`() {
    requestPort(clientPort = 1)
    login(clientPort = 1, existingUsers = emptyList(), existingGames = emptyList())

    requestPort(clientPort = 2)
    login(
      clientPort = 2,
      existingUsers =
        listOf(
          ServerStatus.User(
            username = "tester1",
            ping = Duration.ZERO,
            userId = 1,
            status = UserStatus.IDLE,
            connectionType = LAN,
          )
        ),
      existingGames = emptyList(),
    )

    // Port 1 user was already in the server, so let's make sure they were notified.
    MessageVerifier().apply {
      expectMessage<UserJoined> {
        it ==
          UserJoined(
            messageNumber = 0,
            username = "tester2",
            userId = 2,
            ping = Duration.ZERO,
            connectionType = LAN,
          )
      }
      expectMessage<InformationMessage> {
        it ==
          InformationMessage(
            messageNumber = 0,
            source = "server",
            message = "Server Owner Logged In!",
          )
      }
      receiveAllMessages(receiveAll(onPort = 1))
      flushExpectations()
    }

    createGame(clientPort = 1)

    MessageVerifier().apply {
      expectMessage<CreateGameNotification> {
        it ==
          CreateGameNotification(
            messageNumber = 0,
            username = "tester1",
            romName = "Test Game",
            clientType = "tester_tester",
            gameId = 1,
            val1 = 0,
          )
      }

      expectMessage<GameStatus> {
        it ==
          GameStatus(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            gameStatus = WAITING,
            numPlayers = 1,
            maxPlayers = 8,
          )
      }
      expectMessage<InformationMessage> {
        it ==
          InformationMessage(
            messageNumber = 0,
            source = "server",
            message = "tester1 created game: Test Game",
          )
      }

      receiveAllMessages(receiveAll(onPort = 2))
      flushExpectations()
    }

    send(JoinGameRequest(messageNumber = 5, gameId = 1, connectionType = LAN), fromPort = 2)

    MessageVerifier().apply {
      expectMessage<GameStatus> {
        it ==
          GameStatus(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            gameStatus = WAITING,
            numPlayers = 2,
            maxPlayers = 8,
          )
      }
      expectMessage<JoinGameNotification> {
        it ==
          JoinGameNotification(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            username = "tester2",
            ping = Duration.ZERO,
            userId = 2,
            connectionType = LAN,
          )
      }

      receiveAllMessages(receiveAll(onPort = 1))
      flushExpectations()
    }

    MessageVerifier().apply {
      expectMessage<GameStatus> {
        it ==
          GameStatus(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            gameStatus = WAITING,
            numPlayers = 2,
            maxPlayers = 8,
          )
      }
      expectMessage<PlayerInformation> {
        it ==
          PlayerInformation(
            messageNumber = 0,
            players =
              listOf(
                PlayerInformation.Player(
                  username = "tester1",
                  ping = Duration.ZERO,
                  userId = 1,
                  connectionType = LAN,
                )
              ),
          )
      }
      expectMessage<JoinGameNotification> {
        it ==
          JoinGameNotification(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            username = "tester2",
            ping = Duration.ZERO,
            userId = 2,
            connectionType = LAN,
          )
      }
      expectMessage<GameChatNotification> {
        it ==
          GameChatNotification(
            messageNumber = 0,
            username = "Server",
            message = "Message that appears when a user joins/starts a game!",
          )
      }

      receiveAllMessages(receiveAll(onPort = 2))
      flushExpectations()
    }
  }

  private fun createGame(clientPort: Int) {
    send(CreateGameRequest(messageNumber = 5, romName = "Test Game"), fromPort = clientPort)

    MessageVerifier().apply {
      expectMessage<CreateGameNotification> {
        it ==
          CreateGameNotification(
            messageNumber = 0,
            username = "tester$clientPort",
            romName = "Test Game",
            clientType = "tester_tester",
            gameId = 1,
            val1 = 0,
          )
      }
      expectMessage<GameStatus> {
        it ==
          GameStatus(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            gameStatus = WAITING,
            numPlayers = 1,
            maxPlayers = 8,
          )
      }
      expectMessage<JoinGameNotification> {
        it ==
          JoinGameNotification(
            messageNumber = 0,
            gameId = 1,
            val1 = 0,
            username = "tester$clientPort",
            ping = Duration.ZERO,
            userId = 1,
            connectionType = LAN,
          )
      }
      expectMessage<InformationMessage> { it.message.contains("created game") }
      expectMessage<GameChatNotification>()

      receiveAllMessages(receiveAll(onPort = clientPort))
      flushExpectations()
    }
    //        GameChatNotification(
    //          messageNumber = 0,
    //          username = "Server",
    //          message = "Message that appears when a user joins/starts a game!",
    //        ),
  }

  private fun login(
    clientPort: Int,
    existingUsers: List<ServerStatus.User>,
    existingGames: List<ServerStatus.Game>,
  ) {
    send(
      UserInformation(
        messageNumber = 0,
        username = "tester$clientPort",
        clientType = "tester_tester",
        connectionType = LAN,
      ),
      fromPort = clientPort,
    )

    // Timing handshake.
    expect.that(receive(onPort = clientPort)).isEqualTo(ServerAck(0))
    send(ClientAck(1), fromPort = clientPort)
    expect.that(receive(onPort = clientPort)).isEqualTo(ServerAck(0))
    send(ClientAck(2), fromPort = clientPort)
    expect.that(receive(onPort = clientPort)).isEqualTo(ServerAck(0))
    send(ClientAck(3), fromPort = clientPort)
    expect.that(receive(onPort = clientPort)).isEqualTo(ServerAck(0))
    send(ClientAck(4), fromPort = clientPort)

    MessageVerifier().apply {
      expectMessage<ServerStatus>()
      expectMessage<InformationMessage> { it.message == "Welcome to a new EmuLinker-K Server!" }
      expectMessage<UserJoined> { it.username == "tester$clientPort" }
      receiveAllMessages(receiveAll(onPort = clientPort))
      flushExpectations()
    }
  }

  private fun requestPort(clientPort: Int) {
    // Initial handshake
    channel.writeInbound(
      datagramPacket(
        channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN).also {
          RequestPrivateKailleraPortRequest(protocol = "0.83").writeTo(it)
        },
        fromPort = clientPort,
      )
    )

    expect.that(channel.outboundMessages()).hasSize(1)
    val receivedPacket = channel.readOutbound<DatagramPacket>()

    val response: ConnectMessage = ConnectMessage.parse(receivedPacket.content()).getOrThrow()
    expect.that(response).isEqualTo(RequestPrivateKailleraPortResponse(27888))
  }

  /** Send message to the server. */
  private fun send(message: V086Message, fromPort: Int) {
    val buf = channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN)
    V086Bundle.Single(message).writeTo(buf)
    channel.writeInbound(datagramPacket(buf, fromPort = fromPort))
  }

  /** Receive one message from the server. */
  private fun receive(onPort: Int): V086Message {
    val collectionOfOne = receiveAll(onPort, take = 1)
    expect.that(collectionOfOne).hasSize(1)
    return collectionOfOne.single()
  }

  /** Receive all available messages from the server. */
  private fun receiveAll(onPort: Int, take: Int = Int.MAX_VALUE) =
    buildList<V086Message> {
      val incoming = portToMessages.getOrPut(onPort) { mutableListOf() }
      retrieveMessages(take - incoming.size)

      while (incoming.isNotEmpty() && size < take) {
        add(incoming.removeFirst())
      }
    }

  private val lock = Object()

  private fun retrieveMessages(take: Int) =
    synchronized(lock) {
      var taken = 0
      while (taken < take) {
        var receivedPacket: DatagramPacket? = channel.readOutbound()

        // The server we test against runs on multiple threads, so sometimes we have to wait a few
        // milliseconds for it to catch up.
        if (receivedPacket == null) {
          Thread.sleep(5)
          receivedPacket = channel.readOutbound()
        }
        if (receivedPacket == null) {
          Thread.sleep(50)
          receivedPacket = channel.readOutbound()
        }
        if (receivedPacket == null) {
          Thread.sleep(1000)
          receivedPacket = channel.readOutbound() ?: break
        }

        var lastMessageId =
          portToLastServerMessageId.getOrPut(receivedPacket.recipient().port) { -1 }

        when (val bundle = V086Bundle.parse(receivedPacket.content())) {
          is V086Bundle.Single -> {
            val message = bundle.message
            expect.that(message.messageNumber).isEqualTo(lastMessageId + 1)

            portToMessages
              .getOrPut(receivedPacket.recipient().port) { mutableListOf() }
              .add(message.zeroMessageNumber().zeroDurationFields())
            portToLastServerMessageId[receivedPacket.recipient().port] = message.messageNumber
            taken++
          }

          is V086Bundle.Multi -> {
            val messages =
              bundle.messages
                .filter { it!!.messageNumber > lastMessageId }
                .sortedBy { it!!.messageNumber }
            expect
              .withMessage(
                "It would be strange to receive a bundle of no new messages. Raw bundle: $bundle"
              )
              .that(messages)
              .isNotEmpty()
            for (message in messages) {
              expect.that(message!!.messageNumber).isEqualTo(lastMessageId + 1)

              portToMessages
                .getOrPut(receivedPacket.recipient().port) { mutableListOf() }
                .add(message.zeroMessageNumber().zeroDurationFields())
              portToLastServerMessageId[receivedPacket.recipient().port] = message.messageNumber
              lastMessageId++
              taken++
            }
          }
        }
      }
    }

  private val portToMessages = mutableMapOf<Int, MutableList<V086Message>>()
  private val portToLastServerMessageId = mutableMapOf<Int, Int>()

  private fun datagramPacket(buffer: ByteBuf, fromPort: Int) =
    DatagramPacket(
      Unpooled.wrappedBuffer(buffer),
      /* recipient= */ InetSocketAddress(
        channel.remoteAddress().hostname,
        channel.remoteAddress().port,
      ),
      /* sender= */ InetSocketAddress(channel.localAddress().hostname, fromPort),
    )

  private companion object {
    init {
      // Use log4j as the flogger backend.
      System.setProperty(
        "flogger.backend_factory",
        "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance",
      )
    }
  }
}

private fun V086Message.zeroDurationFields(): V086Message =
  when (this) {
    is ServerStatus -> this.copy(users = this.users.map { it.copy(ping = Duration.ZERO) })
    is JoinGameNotification -> this.copy(ping = Duration.ZERO)
    is UserJoined -> this.copy(ping = Duration.ZERO)
    is PlayerInformation -> this.copy(players = this.players.map { it.copy(ping = Duration.ZERO) })
    else -> this
  }

private fun V086Message.zeroMessageNumber(): V086Message =
  when (this) {
    is CreateGameNotification -> this.copy(messageNumber = 0)
    is GameChatNotification -> this.copy(messageNumber = 0)
    is GameStatus -> this.copy(messageNumber = 0)
    is InformationMessage -> this.copy(messageNumber = 0)
    is JoinGameNotification -> this.copy(messageNumber = 0)
    is PlayerInformation -> this.copy(messageNumber = 0)
    is ServerAck -> this.copy(messageNumber = 0)
    is ServerStatus -> this.copy(messageNumber = 0)
    is UserJoined -> this.copy(messageNumber = 0)
    else -> TODO("implement zero for ${this::class.simpleName}")
  }

class MessageVerifier {

  @PublishedApi internal val expectations = mutableListOf<(Any) -> Boolean>()

  /** expectMessage: Adds an expectation based on a specific type R and a predicate. */
  inline fun <reified R : Any> expectMessage(crossinline predicate: (R) -> Boolean = { true }) {
    expectations.add { message -> message is R && predicate(message) }
  }

  /**
   * receiveMessage: Checks if the object matches any existing expectation. It removes the first
   * matching expectation it finds.
   */
  fun receiveMessage(message: Any) {
    val iterator = expectations.iterator()
    while (iterator.hasNext()) {
      val matches = iterator.next()
      if (matches(message)) {
        iterator.remove()
        return
      }
    }
  }

  fun receiveAllMessages(messages: List<Any>) {
    messages.forEach { receiveMessage(it) }
  }

  /** flushExpectations / flushAssertions: Asserts that the list is empty. */
  fun flushExpectations() {
    assertThat(expectations).isEmpty()
  }
}
