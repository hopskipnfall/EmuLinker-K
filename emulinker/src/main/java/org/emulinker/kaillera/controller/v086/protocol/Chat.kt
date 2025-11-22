package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString

/** Message type ID: `0x07`. */
sealed class Chat : V086Message() {
  abstract val message: String
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is ChatRequest -> ""
        is ChatNotification -> username
      }.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  override fun writeBodyTo(buffer: ByteBuffer) {
    ChatSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    ChatSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x07
  }

  object ChatSerializer : MessageSerializer<Chat> {
    override val messageTypeId: Byte = ID

    override fun write(buffer: ByteBuf, message: Chat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is ChatRequest -> ""
          is ChatNotification -> message.username
        },
      )
      EmuUtil.writeString(buffer, message.message)
    }

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<Chat> {
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val username = buffer.readString()
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = buffer.readString()
      return Result.success(
        if (username.isBlank()) {
          ChatRequest(messageNumber = messageNumber, message = message)
        } else {
          ChatNotification(messageNumber = messageNumber, username = username, message = message)
        }
      )
    }

    fun write(buffer: ByteBuffer, message: Chat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is ChatRequest -> ""
          is ChatNotification -> message.username
        },
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}

/**
 * Message sent by the server containing a server chat message.
 *
 * This shares a message type ID with [ChatRequest]: `0x07`.
 */
data class ChatNotification(
  override var messageNumber: Int,
  val username: String,
  override val message: String,
) : Chat(), ServerMessage

/**
 * Message sent by the client containing a server chat message.
 *
 * This shares a message type ID with [ChatNotification]: `0x07`.
 */
data class ChatRequest(override var messageNumber: Int, override val message: String) :
  Chat(), ClientMessage
