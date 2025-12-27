package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.assertNotNull
import kotlin.time.Duration
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.QuitNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitRequest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
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
open class LoginBenchmark : KoinComponent {
  lateinit var channel: EmbeddedChannel
  lateinit var controller: CombinedKailleraController
  lateinit var userActionsExecutor: ThreadPoolExecutor

  var succeeded = 0L

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
    val port = Random.nextInt(1..1000)
    logger.atInfo().log("starting login on port %d", port)
    val sender = InetSocketAddress("127.0.0.1", port)

    fun sendRequestPrivateKailleraPortRequest() {
      val request = RequestPrivateKailleraPortRequest("0.83")
      val buffer =
        Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, sender)

      // Write inbound
      channel.writeInbound(packet)
    }
    sendRequestPrivateKailleraPortRequest()

    fun receiveRequestPrivateKailleraPortResponse(): RequestPrivateKailleraPortResponse {
      while (true) {
        val response: DatagramPacket = assertNotNull(channel.readOutbound<DatagramPacket>())
        try {
          if (response.recipient().port != port) continue
          val message = ConnectMessage.parse(response.content()).getOrThrow()
          return message as RequestPrivateKailleraPortResponse
        } finally {
          response.release()
        }
      }
    }
    blackhole.consume(receiveRequestPrivateKailleraPortResponse())

    fun sendBundle(bundle: V086Bundle) {
      // TODO(nue): Add past messages to the bundle.
      val buffer = Unpooled.buffer(1024).apply { bundle.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, sender)

      // Write inbound
      channel.writeInbound(packet)
    }

    var lastMessageNumber = -1
    var lastMessageNumberReceived = -1
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

    fun receiveV086Message(): V086Message? {
      while (true) {
        val response = channel.readOutbound<DatagramPacket>() ?: return null // No messages to read.
        if (response.recipient().port != port) {
          response.release()
          continue
        }
        val bundle =
          try {
            if (
              response.content().readableBytes() == 1 &&
                response.content().readByte() == 0x00.toByte()
            ) {
              return null
            }
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

        return when (bundle) {
          is V086Bundle.Single -> bundle.message
          is V086Bundle.Multi -> {
            bundle.messages.maxBy { it?.messageNumber ?: return null }
          }
        }.also {
          if (it != null) lastMessageNumberReceived = it.messageNumber

          logger.atInfo().log("Received: %s", it)
        }
      }
    }

    var message: V086Message? = checkNotNull(receiveV086Message())
    var sawUserJoined = false
    while (message != null) {
      when (message) {
        is ServerAck -> {
          sendBundle(V086Bundle.Single(ClientAck(++lastMessageNumber)))
        }

        is UserJoined -> {
          if (message.username == "testUser$port") sawUserJoined = true
          else logger.atSevere().log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        }

        else -> {}
      }
      message = receiveV086Message()
    }
    check(sawUserJoined) { "Expected to see UserJoined message" }

    sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, message = "peace")))

    var sawQuitNotification = false
    message = receiveV086Message()
    while (message != null) {
      when (message) {
        is QuitNotification -> {
          if (message.username == "testUser$port") {
            sawQuitNotification = true
            logger.atInfo().log("IT IS HERE22222!!!!")
          } else {
            logger.atSevere().log("!@$!@%!#$!@%!@$!@%!!!!")
          }
        }

        else -> {}
      }
      message = receiveV086Message()
    }
    check(sawQuitNotification)

    succeeded++
    logger.atInfo().log("DONE. Succeeded: %d", succeeded)
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
