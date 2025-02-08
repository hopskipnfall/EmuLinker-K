package org.emulinker.kaillera.controller

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
import kotlin.time.Duration.Companion.nanoseconds
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.ServerStatus
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType
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
    assertThat(channel.inboundMessages()).isEmpty()
    assertThat(channel.outboundMessages()).isEmpty()

    stopKoin()
  }

  @Test
  fun `initial handshake`() {
    val clientPort = 1

    val buf = channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN)
    RequestPrivateKailleraPortRequest(protocol = "0.83").writeTo(buf)

    channel.writeInbound(datagramPacket(buf, fromPort = clientPort))

    assertThat(channel.outboundMessages()).hasSize(1)
    val a = channel.readOutbound<DatagramPacket>()

    val response: ConnectMessage = ConnectMessage.parse(a.content().nioBuffer()).getOrThrow()
    assertThat(response).isEqualTo(RequestPrivateKailleraPortResponse(27888))
  }

  @Test
  fun `log into server`() {
    val clientPort = 1

    channel.writeInbound(
      datagramPacket(
        channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN).also {
          RequestPrivateKailleraPortRequest(protocol = "0.83").writeTo(it)
        },
        fromPort = clientPort,
      )
    )

    assertThat(channel.outboundMessages()).hasSize(1)
    val receivedPacket = channel.readOutbound<DatagramPacket>()

    val response: ConnectMessage =
      ConnectMessage.parse(receivedPacket.content().nioBuffer()).getOrThrow()
    assertThat(response).isEqualTo(RequestPrivateKailleraPortResponse(27888))

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

    assertThat(receive()).isEqualTo(ServerStatus(4, users = emptyList(), games = emptyList()))

    assertThat(receive())
      .isEqualTo(
        InformationMessage(5, source = "server", message = "Welcome to a new EmuLinker-K Server!")
      )
    assertThat(receive())
      .isEqualTo(
        InformationMessage(
          6,
          source = "server",
          message = "Edit emulinker.cfg to setup your server configuration",
        )
      )
    assertThat(receive())
      .isEqualTo(
        InformationMessage(
          7,
          source = "server",
          message = "Edit language.properties to setup your login announcements",
        )
      )

    var newPacket = receive()
    assertThat(newPacket).isInstanceOf(InformationMessage::class.java)
    assertThat((newPacket as InformationMessage).message).matches("EmuLinker-K v.*")

    assertThat(receive())
      .isEqualTo(
        InformationMessage(
          9,
          source = "server",
          message =
            "WARNING: This is an unoptimized debug build that should not be used in production.",
        )
      )
    assertThat(receive())
      .isEqualTo(
        InformationMessage(
          10,
          source = "server",
          message = "Welcome Admin! Type /help for a admin command list.",
        )
      )

    // This happens on another thread, so we have to wait for it to catch up.. There should be a
    // better way to do this.
    Thread.sleep(50)

    newPacket = receive()
    assertThat(newPacket).isInstanceOf(UserJoined::class.java)
    check(newPacket is UserJoined)
    assertThat(newPacket.username).isEqualTo("tester")
    assertThat(newPacket.userId).isEqualTo(1)
    assertThat(newPacket.connectionType).isEqualTo(ConnectionType.LAN)
    assertThat(newPacket.ping).isGreaterThan(1.nanoseconds)

    assertThat(receive())
      .isEqualTo(InformationMessage(12, source = "server", message = "Server Owner Logged In!"))
  }

  private fun send(message: V086Message, fromPort: Int = 1) {
    channel.writeInbound(
      datagramPacket(
        channel.alloc().buffer().order(ByteOrder.LITTLE_ENDIAN).also {
          V086Bundle(arrayOf(message)).writeTo(it)
        },
        fromPort = fromPort,
      )
    )
  }

  private fun receive(onPort: Int = 1): V086Message {
    val receivedPacket = channel.readOutbound<DatagramPacket>()
    assertThat(receivedPacket.recipient().port == onPort)
    // TODO(nue): Don't just pull the first message..
    val message = V086Bundle.parse(receivedPacket.content().nioBuffer()).messages.first()
    return checkNotNull(message)
  }

  private fun datagramPacket(buffer: ByteBuf, fromPort: Int) =
    DatagramPacket(
      Unpooled.wrappedBuffer(buffer),
      /* recipient= */ InetSocketAddress(
        channel.remoteAddress().hostname,
        channel.remoteAddress().port,
      ),
      /* sender= */ InetSocketAddress(channel.localAddress().hostname, fromPort),
    )
}
