package org.emulinker.kaillera.controller

import com.google.common.truth.Expect
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteOrder
import kotlin.time.Duration
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
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
import org.junit.Ignore
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

  // private val threadPoolExecutor: ThreadPoolExecutor by inject(named("userActionsExecutor"))

  private lateinit var channel: EmbeddedChannel

  @Before
  fun before() {
    channel =
      EmbeddedChannel(
        // io.netty.handler.logging.LoggingHandler(),
        server
      )
  }

  @After
  fun after() {
    // TODO:
    // threadPoolExecutor.shutdown()
    // threadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS)

    // Make sure we didn't skip any messages.
    retrieveMessages(Int.MAX_VALUE)
    for ((port, messages) in portToMessages) {
      expect.withMessage("Port $port should have no outstanding messages").that(messages).isEmpty()
    }
    expect.that(channel.inboundMessages()).isEmpty()
  }

  @Test
  fun `log into server and create a game`() {
    requestPort(clientPort = 1)

    login(clientPort = 1, existingUsers = emptyList(), existingGames = emptyList())

    createGame(clientPort = 1)
  }

  @Test
  @Ignore // It's broken!
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

    expect
      .that(receiveAll(onPort = 1, take = 2))
      .containsExactly(
        UserJoined(
          messageNumber = 0,
          username = "tester2",
          userId = 2,
          ping = Duration.ZERO,
          connectionType = LAN,
        ),
        InformationMessage(
          messageNumber = 0,
          source = "server",
          message = "Server Owner Logged In!",
        ),
      )

    createGame(clientPort = 1)

    expect
      .that(receiveAll(onPort = 2, take = 3))
      .containsExactly(
        CreateGameNotification(
          messageNumber = 0,
          username = "tester1",
          romName = "Test Game",
          clientType = "tester_tester",
          gameId = 1,
          val1 = 0,
        ),
        GameStatus(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          gameStatus = WAITING,
          numPlayers = 1,
          maxPlayers = 8,
        ),
        InformationMessage(
          messageNumber = 0,
          source = "server",
          message = "tester1 created game: Test Game",
        ),
      )

    send(JoinGameRequest(messageNumber = 5, gameId = 1, connectionType = LAN), fromPort = 2)

    expect
      .that(receiveAll(onPort = 1, take = 2))
      .containsExactly(
        GameStatus(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          gameStatus = WAITING,
          numPlayers = 2,
          maxPlayers = 8,
        ),
        JoinGameNotification(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          username = "tester2",
          ping = Duration.ZERO,
          userId = 2,
          connectionType = LAN,
        ),
      )

    expect
      .that(receiveAll(onPort = 2, take = 4))
      .containsExactly(
        GameStatus(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          gameStatus = WAITING,
          numPlayers = 2,
          maxPlayers = 8,
        ),
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
        ),
        JoinGameNotification(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          username = "tester2",
          ping = Duration.ZERO,
          userId = 2,
          connectionType = LAN,
        ),
        GameChatNotification(
          messageNumber = 0,
          username = "Server",
          message = "Message that appears when a user joins/starts a game!",
        ),
      )
  }

  private fun createGame(clientPort: Int) {
    send(CreateGameRequest(messageNumber = 5, romName = "Test Game"), fromPort = clientPort)

    expect
      .that(receiveAll(onPort = clientPort, take = 3))
      .containsExactly(
        CreateGameNotification(
          messageNumber = 0,
          username = "tester$clientPort",
          romName = "Test Game",
          clientType = "tester_tester",
          gameId = 1,
          val1 = 0,
        ),
        GameStatus(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          gameStatus = WAITING,
          numPlayers = 1,
          maxPlayers = 8,
        ),
        PlayerInformation(messageNumber = 0, players = emptyList()),
      )

    expect
      .that(receive(onPort = 1))
      .isEqualTo(
        JoinGameNotification(
          messageNumber = 0,
          gameId = 1,
          val1 = 0,
          username = "tester$clientPort",
          ping = Duration.ZERO,
          userId = 1,
          connectionType = LAN,
        )
      )

    expect
      .that(receiveAll(onPort = clientPort, take = 2))
      .containsExactly(
        InformationMessage(
          messageNumber = 0,
          source = "server",
          message = "tester$clientPort created game: Test Game",
        ),
        GameChatNotification(
          messageNumber = 0,
          username = "Server",
          message = "Message that appears when a user joins/starts a game!",
        ),
      )
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

    expect
      .that(receiveAll(onPort = clientPort, take = 9))
      .containsExactly(
        ServerStatus(0, users = existingUsers, games = existingGames),
        InformationMessage(0, source = "server", message = "Welcome to a new EmuLinker-K Server!"),
        InformationMessage(
          0,
          source = "server",
          message = "Edit emulinker.cfg to setup your server configuration",
        ),
        InformationMessage(
          0,
          source = "server",
          message = "Edit language.properties to setup your login announcements",
        ),
        InformationMessage(
          0,
          source = "server",
          message =
            "${releaseInfo.productName} v${releaseInfo.version}: ${releaseInfo.websiteString}",
        ),
        InformationMessage(
          0,
          source = "server",
          message =
            "WARNING: This is an unoptimized debug build that should not be used in production.",
        ),
        InformationMessage(
          0,
          source = "server",
          message = "Welcome Admin! Type /help for a admin command list.",
        ),
        UserJoined(
          messageNumber = 0,
          username = "tester$clientPort",
          userId = clientPort, // I'm doing this on purpose.
          connectionType = LAN,
          ping = Duration.ZERO,
        ),
        InformationMessage(0, source = "server", message = "Server Owner Logged In!"),
      )
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

    val response: ConnectMessage =
      ConnectMessage.parse(receivedPacket.content().nioBuffer()).getOrThrow()
    expect.that(response).isEqualTo(RequestPrivateKailleraPortResponse(27888))
  }

  /** Send message to the server. */
  private fun send(message: V086Message, fromPort: Int) {
    val buf = channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN)
    V086Bundle(arrayOf(message)).writeTo(buf)
    channel.writeInbound(datagramPacket(buf, fromPort = fromPort))
  }

  /** Receive one message from the server. */
  private fun receive(onPort: Int): V086Message {
    val collectionOfOne = receiveAll(onPort, take = 1)
    expect.that(collectionOfOne).hasSize(1)
    return collectionOfOne.single()
  }

  /** Receive all available messages from the server. */
  private fun receiveAll(onPort: Int, take: Int) =
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

        val bundle = V086Bundle.parse(receivedPacket.content().nioBuffer())
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
