package org.emulinker.kaillera.controller

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.logging.LoggingHandler
import java.net.InetSocketAddress
import java.nio.ByteOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
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
import org.emulinker.kaillera.controller.v086.protocol.PlayerInformation
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.ServerStatus
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus.WAITING
import org.emulinker.kaillera.pico.koinModule
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject

class CombinedKailleraControllerTest : ProtocolBaseTest(), KoinTest {
  private val server: CombinedKailleraController by inject()
  private lateinit var channel: EmbeddedChannel

  @Before
  fun before() {
    startKoin { modules(koinModule, ActionModule) }
    channel = EmbeddedChannel(LoggingHandler(), server)
  }

  @After
  fun after() {
    // Make sure we didn't skip any messages.
    assertThat(receiveAll()).isEmpty()

    stopKoin()
  }

  @Test
  fun `log into server and create a game`() {
    requestPort()

    login()

    createGame()
  }

  private fun createGame() {
    send(CreateGameRequest(messageNumber = 5, romName = "Test Game"))

    assertThat(receiveAll())
      .containsExactly(
        CreateGameNotification(
          messageNumber = 13,
          username = "tester",
          romName = "Test Game",
          clientType = "tester_tester",
          gameId = 1,
          val1 = 0,
        ),
        GameStatus(
          messageNumber = 14,
          gameId = 1,
          val1 = 0,
          gameStatus = WAITING,
          numPlayers = 1,
          maxPlayers = 8,
        ),
        PlayerInformation(messageNumber = 15, players = emptyList()),
        JoinGameNotification(
          messageNumber = 16,
          gameId = 1,
          val1 = 0,
          username = "tester",
          ping = 2.milliseconds,
          userId = 1,
          connectionType = ConnectionType.LAN,
        ),
        InformationMessage(
          messageNumber = 17,
          source = "server",
          message = "tester created game: Test Game",
        ),
        GameChatNotification(
          messageNumber = 18,
          username = "Server",
          message = "Message that appears when a user joins/starts a game!",
        ),
      )
  }

  private fun login() {
    send(
      UserInformation(
        messageNumber = 0,
        username = "tester",
        clientType = "tester_tester",
        connectionType = ConnectionType.LAN,
      )
    )

    // Timing handshake.
    assertThat(receive()).isEqualTo(ServerAck(0))
    send(ClientAck(1))
    assertThat(receive()).isEqualTo(ServerAck(1))
    send(ClientAck(2))
    assertThat(receive()).isEqualTo(ServerAck(2))
    send(ClientAck(3))
    assertThat(receive()).isEqualTo(ServerAck(3))
    send(ClientAck(4))

    assertThat(receiveAll(take = 4))
      .containsExactly(
        ServerStatus(4, users = emptyList(), games = emptyList()),
        InformationMessage(5, source = "server", message = "Welcome to a new EmuLinker-K Server!"),
        InformationMessage(
          6,
          source = "server",
          message = "Edit emulinker.cfg to setup your server configuration",
        ),
        InformationMessage(
          7,
          source = "server",
          message = "Edit language.properties to setup your login announcements",
        ),
      )

    var newPacket = receive()
    assertThat(newPacket).isInstanceOf(InformationMessage::class.java)
    assertThat((newPacket as InformationMessage).message).matches("EmuLinker-K v.*")

    assertThat(receiveAll(take = 2))
      .containsExactly(
        InformationMessage(
          9,
          source = "server",
          message =
            "WARNING: This is an unoptimized debug build that should not be used in production.",
        ),
        InformationMessage(
          10,
          source = "server",
          message = "Welcome Admin! Type /help for a admin command list.",
        ),
      )

    newPacket = receive()
    assertThat(newPacket).isInstanceOf(UserJoined::class.java)
    check(newPacket is UserJoined)
    // TODO(nue): Bind a fake Clock so measured ping is consistent between invocations.
    assertThat(newPacket.ping).isIn(Range.closed(1.nanoseconds, 1.seconds))
    assertThat(newPacket.copy(ping = Duration.ZERO))
      .isEqualTo(
        UserJoined(
          messageNumber = 11,
          username = "tester",
          userId = 1,
          connectionType = ConnectionType.LAN,
          ping = Duration.ZERO,
        )
      )

    assertThat(receive())
      .isEqualTo(InformationMessage(12, source = "server", message = "Server Owner Logged In!"))
  }

  private fun requestPort() {
    // Initial handshake
    channel.writeInbound(
      datagramPacket(
        channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN).also {
          RequestPrivateKailleraPortRequest(protocol = "0.83").writeTo(it)
        }
      )
    )

    assertThat(channel.outboundMessages()).hasSize(1)
    val receivedPacket = channel.readOutbound<DatagramPacket>()

    val response: ConnectMessage =
      ConnectMessage.parse(receivedPacket.content().nioBuffer()).getOrThrow()
    assertThat(response).isEqualTo(RequestPrivateKailleraPortResponse(27888))
  }

  /** Send message to the server. */
  private fun send(message: V086Message, fromPort: Int = 1) {
    val buf = channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN)
    V086Bundle(arrayOf(message)).writeTo(buf)
    channel.writeInbound(datagramPacket(buf, fromPort = fromPort))
  }

  /** Receive one message from the server. */
  private fun receive(onPort: Int = 1): V086Message = receiveAll(onPort, take = 1).first()

  /** Receive all available messages from the server. */
  private fun receiveAll(onPort: Int = 1, take: Int = Integer.MAX_VALUE) =
    buildList<V086Message> {
      while (size < take) {
        var receivedPacket: DatagramPacket? = channel.readOutbound()

        // The server we test against runs on multiple threads, so sometimes we have to wait a few
        // milliseconds for it to catch up.
        if (receivedPacket == null) {
          Thread.sleep(10)
          receivedPacket = channel.readOutbound()
        }
        if (receivedPacket == null) {
          Thread.sleep(50)
          receivedPacket = channel.readOutbound() ?: break
        }

        assertThat(receivedPacket.recipient().port == onPort)
        // TODO(nue): Don't just pull the first message..
        val message = V086Bundle.parse(receivedPacket.content().nioBuffer()).messages.first()
        add(message!!)
      }
    }

  private fun datagramPacket(buffer: ByteBuf, fromPort: Int = 1) =
    DatagramPacket(
      Unpooled.wrappedBuffer(buffer),
      /* recipient= */ InetSocketAddress(
        channel.remoteAddress().hostname,
        channel.remoteAddress().port,
      ),
      /* sender= */ InetSocketAddress(channel.localAddress().hostname, fromPort),
    )
}
