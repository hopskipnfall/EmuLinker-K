package org.emulinker.eval.client

import com.google.common.flogger.FluentLogger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_HELLO
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage_HELLOD00D
import org.emulinker.kaillera.controller.v086.LastMessageBuffer
import org.emulinker.kaillera.controller.v086.V086Controller
import org.emulinker.kaillera.controller.v086.protocol.*
import org.emulinker.kaillera.model.ConnectionType

private val logger = FluentLogger.forEnclosingClass()

class EvalClient(
    private val username: String, private val connectControllerAddress: InetSocketAddress
) : Closeable {
  private val lastMessageBuffer = LastMessageBuffer(V086Controller.MAX_BUNDLE_SIZE)

  var socket: ConnectedDatagramSocket? = null

  private val outMutex = Mutex()

  private var killSwitch = false

  var lastIncomingMessageNumber = -1
  var lastOutgoingMessageNumber = -1

  private var latestServerStatus: ServerStatus? = null

  /** Interacts with the ConnectController server and */
  suspend fun connectToDedicatedPort() {
    val selectorManager = SelectorManager(Dispatchers.IO)
    socket = aSocket(selectorManager).udp().connect(connectControllerAddress)

    val allocatedPort =
        socket?.use { connectedSocket ->
          logger.atInfo().log("Started new eval client at %s", connectedSocket.localAddress)

          sendConnectMessage(ConnectMessage_HELLO(protocol = "0.83"))

          val response = ConnectMessage.parse(connectedSocket.receive().packet.readByteBuffer())
          logger.atInfo().log("========== Received message: %s", response)
          require(response is ConnectMessage_HELLOD00D)

          response.port
        }
    requireNotNull(allocatedPort)

    socket =
        aSocket(selectorManager)
            .udp()
            .connect(InetSocketAddress(connectControllerAddress.hostname, allocatedPort))
    logger.atInfo().log("Changing connection to: %s", socket!!.remoteAddress)
  }

  /** Interacts in the server */
  @OptIn(DelicateCoroutinesApi::class) // GlobalScope.
  suspend fun start() {

    GlobalScope.launch(Dispatchers.IO) {
      while (!killSwitch) {
        val response = V086Bundle.parse(socket!!.receive().packet.readByteBuffer())
        handleIncoming(response)
      }
      logger.atInfo().log("EvalClient shut down.")
    }

    sendWithMessageId {
      UserInformation(messageNumber = it, username, "Fake Client", ConnectionType.LAN)
    }
  }

  private suspend fun handleIncoming(bundle: V086Bundle) {
    while (true) {
      val nextMessage =
          bundle.messages.firstOrNull { it!!.messageNumber == lastIncomingMessageNumber + 1 }
              ?: break
      lastIncomingMessageNumber++

      logger.atInfo().log("========== Received message: %s", nextMessage)

      when (nextMessage) {
        is ServerACK -> {
          sendWithMessageId { ClientACK(messageNumber = it) }
        }
        is ServerStatus -> {
          latestServerStatus = nextMessage
        }
        is InformationMessage -> {}
        is UserJoined -> {}
        is CreateGame_Notification -> {}
        is GameStatus -> {}
        is PlayerInformation -> {}
        is JoinGame_Notification -> {}
        else -> {
          logger.atSevere().log("Unexpected message type: %s", nextMessage)
        }
      }
    }
  }

  suspend fun createGame() {
    sendWithMessageId {
      CreateGame_Request(
          messageNumber = it, romName = "Nintendo All-Star! Dairantou Smash Brothers (J)")
    }
  }

  suspend fun joinAnyAvailableGame() {
    // TODO(nue): Make it listen to individual game creation updates too.
    val games = requireNotNull(latestServerStatus?.games)
    sendWithMessageId {
      JoinGame_Request(
          messageNumber = it, gameId = games.first().gameId, connectionType = ConnectionType.LAN)
    }
  }

  override fun close() {
    logger.atInfo().log("Shutting down EvalClient.")
    killSwitch = true
    socket?.close()
  }

  private suspend fun sendConnectMessage(message: ConnectMessage) {
    socket!!.send(Datagram(ByteReadPacket(message.toBuffer()!!), socket!!.remoteAddress))
  }

  private suspend fun sendWithMessageId(messageIdToMessage: (messageNumber: Int) -> V086Message) {
    outMutex.withLock {
      lastOutgoingMessageNumber++
      val messageAsArray: Array<V086Message?> =
          arrayOf(messageIdToMessage(lastOutgoingMessageNumber))

      val outBuffer = ByteBuffer.allocateDirect(4096)
      lastMessageBuffer.fill(messageAsArray, messageAsArray.size)
      val outBundle = V086Bundle(messageAsArray, messageAsArray.size)
      outBundle.writeTo(outBuffer)
      (outBuffer as Buffer).flip()
      socket!!.send(Datagram(ByteReadPacket(outBuffer), socket!!.remoteAddress))
    }
  }
}
