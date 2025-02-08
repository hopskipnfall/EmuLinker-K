package org.emulinker.kaillera.controller

import com.google.common.truth.Truth.assertThat
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetSocketAddress
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.pico.koinModule
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.test.KoinTest
import org.koin.test.inject

class CombinedKailleraControllerTest : KoinTest {

  val server: CombinedKailleraController by inject()
  lateinit var channel: EmbeddedChannel

  @Before
  fun before() {

    // Use log4j as the flogger backend.
    System.setProperty(
      "flogger.backend_factory",
      "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance",
    )

    startKoin { modules(koinModule, ActionModule) }
    channel = EmbeddedChannel(server)
  }

  @Test
  fun test() {
    assertThat(channel.isActive).isTrue()

    val packet =
      DatagramPacket(
        Unpooled.wrappedBuffer(RequestPrivateKailleraPortRequest(protocol = "0.83").toBuffer()),
        /* recipient= */ InetSocketAddress(
          channel.remoteAddress().hostname,
          channel.remoteAddress().port,
        ),
        /* sender= */ InetSocketAddress(
          channel.localAddress().hostname,
          channel.localAddress().port,
        ),
      )
    assertThat(packet.sender()).isNotNull()
    channel.writeInbound(packet)

    assertThat(channel.outboundMessages()).hasSize(1)
    val a = channel.readOutbound<DatagramPacket>()
    assertThat(ConnectMessage.parse(a.content().nioBuffer()).getOrThrow())
      .isInstanceOf(RequestPrivateKailleraPortResponse::class.java)
  }
}
