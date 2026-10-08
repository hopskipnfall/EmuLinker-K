package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.util.ProtocolCharset

/**
 * Abstract class representing a message for connecting to the server.
 *
 * The connection handshake goes as follows:
 * - Client sends a [RequestPrivateKailleraPortRequest]
 * - Server responds with [RequestPrivateKailleraPortResponse], which includes a private port
 *   allocated for that client.
 *
 * After that point the client never interacts with the connect server. There are other subtypes
 * [ConnectMessage_ServerFull], [ConnectMessage_PING], and [ConnectMessage_PONG] which I do not
 * believe are used by the connect server and probably shouldn't inherit from this class.
 */
sealed class ConnectMessage : ByteBufferMessage {
  protected abstract val iD: String?

  companion object {

    fun parse(buffer: ByteBuf): Result<ConnectMessage> {
      val messageStr =
        buffer.readCharSequence(buffer.readableBytes(), ProtocolCharset.value).toString()

      try {
        return parseString(buffer, messageStr)
      } catch (e: IllegalArgumentException) {
        // Anything starting with a known prefix but otherwise malformed (wrong length, missing
        // terminator, ...). Datagrams come from untrusted senders, so never let this propagate.
        buffer.resetReaderIndex()
        return failure(MessageFormatException("Malformed connect message", e))
      }
    }

    private fun parseString(buffer: ByteBuf, messageStr: String): Result<ConnectMessage> {
      when {
        messageStr.startsWith(ConnectMessage_ServerFull.ID) -> {
          return success(ConnectMessage_ServerFull.parse(messageStr))
        }
        messageStr.startsWith(RequestPrivateKailleraPortResponse.ID) -> {
          return success(RequestPrivateKailleraPortResponse.parse(messageStr))
        }
        messageStr.startsWith(RequestPrivateKailleraPortRequest.ID) -> {
          return success(RequestPrivateKailleraPortRequest.parse(messageStr))
        }
        messageStr.startsWith(ConnectMessage_PING.ID) -> {
          return success(ConnectMessage_PING.parse(messageStr))
        }
        messageStr.startsWith(ConnectMessage_PONG.ID) -> {
          return success(ConnectMessage_PONG.parse(messageStr))
        }
        else -> {
          buffer.resetReaderIndex()
          return failure(MessageFormatException("Unrecognized connect message"))
        }
      }
    }
  }
}
