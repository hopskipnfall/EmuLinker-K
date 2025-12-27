package org.emulinker.kaillera.controller

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PING
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PONG
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.pico.koinModule
import org.junit.Rule
import org.koin.test.KoinTestRule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class PingBenchmark {
    lateinit var channel: EmbeddedChannel

    @Setup(Level.Trial)
    fun setup() {
      // TODO: Use Koin to inject a copy of CombinedKailleraController.
      // You may consider using the koin modules org.emulinker.kaillera.pico.koinModule and org.emulinker.kaillera.controller.v086.action.ActionModule
      channel = EmbeddedChannel(server)
    }

    @Benchmark
    fun serverPing(blackhole: Blackhole) {
    // Create a PING packet
    val buffer = Unpooled.buffer()
      ConnectMessage_PING.writeTo(buffer)
    val packet = DatagramPacket(buffer, RECIPIENT, SENDER)

    // Write inbound
    channel.writeInbound(packet)

    val response: DatagramPacket = assertNotNull(channel.readOutbound<DatagramPacket>())
      blackhole.consume(response)
    }

  private companion object {
    val SENDER = InetSocketAddress("127.0.0.1", 12345)
    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)
  }
}
