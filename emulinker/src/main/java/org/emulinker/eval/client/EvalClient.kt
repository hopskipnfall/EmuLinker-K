package org.emulinker.eval.client

import com.google.common.flogger.FluentLogger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_HELLO
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_HELLOD00D
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.v086.LastMessageBuffer
import org.emulinker.kaillera.controller.v086.V086Controller
import org.emulinker.kaillera.controller.v086.protocol.*
import org.emulinker.kaillera.model.ConnectionType

private val logger = FluentLogger.forEnclosingClass()

class EvalClient(private val connectControllerAddress: InetSocketAddress) {
  private val lastMessageBuffer = LastMessageBuffer(V086Controller.MAX_BUNDLE_SIZE)

  var socket: ConnectedDatagramSocket? = null

  suspend fun joinServer() {
    val selectorManager = SelectorManager(Dispatchers.IO)
    socket = aSocket(selectorManager).udp().connect(connectControllerAddress)

    val allocatedPort =
        socket?.use { connectedSocket ->
          logger.atInfo().log("Started new eval client at %s", connectedSocket.localAddress)

          send(ConnectMessage_HELLO(protocol = "0.83"))

          val response = ConnectMessage.parse(connectedSocket.receive().packet.readByteBuffer())
          require(response is ConnectMessage_HELLOD00D)

          response.port
        }
    requireNotNull(allocatedPort)

    socket =
        aSocket(selectorManager)
            .udp()
            .connect(InetSocketAddress(connectControllerAddress.hostname, allocatedPort))
    logger.atInfo().log("Changing connection to: %s", socket!!.remoteAddress)

    // I'm trying to follow the same server/client handshake as in
    // https://gist.github.com/hopskipnfall/44750063d7ca95edb9fa6b226ee6ff5a.

    send(
        arrayOf(UserInformation(messageNumber = 0, "username1", "Fake Client", ConnectionType.LAN)))

    var response = V086Bundle.parse(socket!!.receive().packet.readByteBuffer())
    logger.atInfo().log("Received! %s", response)
    assertContainsExactly(response, arrayOf(ServerACK(0)))

    send(arrayOf(ClientACK(messageNumber = 1)))

    response = V086Bundle.parse(socket!!.receive().packet.readByteBuffer())
    logger.atInfo().log("Received! %s", response)
    assertContainsExactly(response, arrayOf(ServerACK(1), ServerACK(0)))

    send(arrayOf(ClientACK(messageNumber = 2)))

    response = V086Bundle.parse(socket!!.receive().packet.readByteBuffer())
    logger.atInfo().log("Received! %s", response)
    assertContainsExactly(response, arrayOf(ServerACK(2), ServerACK(1), ServerACK(0)))

    send(arrayOf(ClientACK(messageNumber = 3)))

    response = V086Bundle.parse(socket!!.receive().packet.readByteBuffer())
    logger.atInfo().log("Received! %s", response)
    assertContainsExactly(response, arrayOf(ServerACK(3), ServerACK(2), ServerACK(1), ServerACK(0)))

    send(arrayOf(ClientACK(messageNumber = 4)))
  }

  private suspend fun send(message: ByteBufferMessage) {
    socket!!.send(Datagram(ByteReadPacket(message.toBuffer()!!), socket!!.remoteAddress))
  }

  private suspend fun send(messages: Array<V086Message?>) {
    val outBuffer = ByteBuffer.allocateDirect(4096)
    lastMessageBuffer.fill(messages, messages.size)
    val outBundle = V086Bundle(messages, messages.size)
    outBundle.writeTo(outBuffer)
    (outBuffer as Buffer).flip()
    socket!!.send(Datagram(ByteReadPacket(outBuffer), socket!!.remoteAddress))
  }

  private fun assertContainsExactly(actual: V086Bundle, expected: Array<V086Message>) {
    require(actual.numMessages == expected.size)
    require(actual.messages.contentEquals(expected))
  }
}
