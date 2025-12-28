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

  private var port = 0

  @Benchmark
  fun login(blackhole: Blackhole) {
    port++
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
        channel.flush()
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

    val messageIterator =
      iterator<V086Message?> {
        while (true) {
          val packet: DatagramPacket? = channel.readOutbound<DatagramPacket>()
          if (packet == null) {
            yield(null)
            continue
          }

          try {
            val content = packet.content()
            if (content.readableBytes() <= 1) {
              logger.atInfo().log("Single byte or less!")
              yield(null)
              continue
            }
            if (packet.recipient().port != port) {
              logger
                .atSevere()
                .log(
                  "RECEIVED PACKET FOR WRONG USER: %d (me %d), message=%s",
                  packet.recipient().port,
                  port,
                  V086Bundle.parse(packet.content())
                )
              continue
            }

            when (val bundle = V086Bundle.parse(content, lastMessageID = lastMessageNumberReceived)) {
              is V086Bundle.Single ->
                yield(bundle.message.also { lastMessageNumberReceived = it.messageNumber

                  logger.atInfo().log("Received to %d message=%s", port, it)
                })
              is V086Bundle.Multi -> {
                for (m in bundle.messages.sortedBy { it!!.messageNumber }) yield(
                  m.also {
                    lastMessageNumberReceived = it!!.messageNumber
                    logger.atInfo().log("Received to %d message=%s", port, it)
                  }
                )
              }
            }
          } finally {
            packet.release()
          }
        }
      }

    var message: V086Message? = checkNotNull(messageIterator.next())
    var sawUserJoined = false
    while (!sawUserJoined) {
      if (message == null ){
        Thread.yield()
        if (Thread.interrupted()) throw InterruptedException()
      }
      when (message) {
        is ServerAck -> {
          sendBundle(V086Bundle.Single(ClientAck(++lastMessageNumber)))
        }

        is UserJoined -> {
          if (message.username == "testUser$port") {
            sawUserJoined = true
            logger.atInfo().log("FOUND USERJOINED")
          }
          else {
          }
        }

        else -> {}
      }
      message = messageIterator.next()
    }
    check(sawUserJoined) { "Expected to see UserJoined message" }

    sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, message = "peace")))

    var sawQuitNotification = false
    message = messageIterator.next()
    while (!sawQuitNotification) {
      if (message == null ){
        Thread.yield()
        if (Thread.interrupted()) throw InterruptedException()
      }

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
      message = messageIterator.next()
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
