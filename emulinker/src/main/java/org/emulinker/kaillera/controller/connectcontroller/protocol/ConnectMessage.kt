package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.pico.AppModule

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
        try {
          buffer.readString(buffer.readableBytes(), AppModule.charsetDoNotUse)
        } catch (e: CharacterCodingException) {
          return failure(
            MessageFormatException("Invalid bytes received: failed to decode to a string!", e)
          )
        }

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
