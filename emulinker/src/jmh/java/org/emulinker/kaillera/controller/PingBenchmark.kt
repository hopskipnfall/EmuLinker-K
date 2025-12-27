package org.emulinker.kaillera.controller

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.time.Duration
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PING
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_PONG
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.pico.koinModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
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
open class PingBenchmark : KoinComponent {
  lateinit var channel: EmbeddedChannel
  private val controller: CombinedKailleraController by inject()

  @Setup(Level.Trial)
  fun setup() {
    startKoin {
      allowOverride(true)
      modules(koinModule, ActionModule, module { single<AccessManager> { FakeAccessManager() } })
    }
    channel = EmbeddedChannel(controller)
  }

  @TearDown(Level.Trial)
  fun teardown() {
    stopKoin()
  }

  @Benchmark
  fun serverPing(blackhole: Blackhole) {
    val buffer =
      Unpooled.buffer(ConnectMessage_PING.bodyBytesPlusMessageIdType).apply {
        ConnectMessage_PING.writeTo(this)
      }
    val packet = DatagramPacket(buffer, RECIPIENT, SENDER)

    // Write inbound
    channel.writeInbound(packet)

    val response: DatagramPacket = assertNotNull(channel.readOutbound<DatagramPacket>())
    check(ConnectMessage.parse(response.content()).getOrThrow() == ConnectMessage_PONG) {
      "Wrong message type!"
    }
    response.release()
    blackhole.consume(response)
  }

  private companion object {
    val SENDER = InetSocketAddress("127.0.0.1", 12345)
    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)
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
