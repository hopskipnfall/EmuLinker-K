package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.time.Duration
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086BundleFormatException
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.koinModule
import org.emulinker.util.EmuUtil.dumpToByteArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
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
open class LoginBenchmark : KoinComponent {
  lateinit var channel: EmbeddedChannel
  lateinit var controller: CombinedKailleraController
  lateinit var userActionsExecutor: ThreadPoolExecutor

  @Setup(Level.Trial)
  fun setup() {
    startKoin {
      allowOverride(true)
      modules(koinModule, ActionModule, module { single<AccessManager> { FakeAccessManager() } })
    }
    controller = get()
    userActionsExecutor = get(named("userActionsExecutor"))
    channel = EmbeddedChannel(controller)
  }

  @TearDown(Level.Trial)
  fun teardown() {
    channel.close()
    controller.stop()
    userActionsExecutor.shutdown()
    stopKoin()
  }

  @Benchmark
  fun login(blackhole: Blackhole) {
    fun sendRequestPrivateKailleraPortRequest() {
      val request = RequestPrivateKailleraPortRequest("0.83")
      val buffer =
        Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, SENDER)

      // Write inbound
      channel.writeInbound(packet)
    }
    sendRequestPrivateKailleraPortRequest()

    fun receiveRequestPrivateKailleraPortResponse(): RequestPrivateKailleraPortResponse {
      val response: DatagramPacket = assertNotNull(channel.readOutbound<DatagramPacket>())
      val message = ConnectMessage.parse(response.content()).getOrThrow()
      response.release()

      return message as RequestPrivateKailleraPortResponse
    }
    blackhole.consume(receiveRequestPrivateKailleraPortResponse())

    fun sendBundle(bundle: V086Bundle) {
      // TODO(nue): Add past messages to the bundle.
      val buffer = Unpooled.buffer(1024).apply { bundle.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, SENDER)

      // Write inbound
      channel.writeInbound(packet)
    }
    var lastMessageNumber = -1
    var lastMessageNumberReceived = -1
    sendBundle(
      V086Bundle.Single(
        UserInformation(
          messageNumber = lastMessageNumber++,
          username = "testUser",
          clientType = "Test Client",
          connectionType = ConnectionType.LAN,
        )
      )
    )

    fun receiveV086Message(): V086Message {
      val response = assertNotNull(channel.readOutbound<DatagramPacket>())
      val bundle =
        try {
          V086Bundle.parse(response.content(), lastMessageID = lastMessageNumberReceived)
        } catch (e: V086BundleFormatException) {
          val c = response.content()
          c.resetReaderIndex()
          logger
            .atSevere()
            .withCause(e)
            .log(
              "Failed to parse! ReadableBytes=%d asHexString=%s",
              c.readableBytes(),
              c.dumpToByteArray().toHexString(),
            )
          throw e
        } finally {
          response.release()
        }

      return checkNotNull(
          when (bundle) {
            is V086Bundle.Single -> bundle.message
            is V086Bundle.Multi -> {
              bundle.messages.maxBy { it!!.messageNumber }
            }
          }
        )
        .also { lastMessageNumberReceived = it.messageNumber }
    }
    var message = receiveV086Message()
    while (message is ServerAck) {
      sendBundle(V086Bundle.Single(ClientAck(lastMessageNumber++)))

      message = receiveV086Message()
    }

    TODO("Handle UserJoined message, which $message should be")
  }

  private companion object {
    init {
      AppModule.charsetDoNotUse = Charsets.UTF_8
    }

    val SENDER = InetSocketAddress("127.0.0.1", 12345)
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
