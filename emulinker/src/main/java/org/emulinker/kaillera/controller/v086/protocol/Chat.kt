package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

/** Message ID `0x07`. */
sealed class Chat : V086Message() {
  abstract val message: String
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is Request -> ""
        is Notification -> username
      }.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    ChatSerializer.write(buffer, this)
  }

  data class Notification
  constructor(override val messageNumber: Int, val username: String, override val message: String) :
    Chat()

  data class Request constructor(override val messageNumber: Int, override val message: String) :
    Chat()

  companion object {
    const val ID: Byte = 0x07

    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<Chat> {
      return ChatSerializer.read(buffer, messageNumber)
    }
  }

  object ChatSerializer : MessageSerializer<Chat> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<Chat> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val username = EmuUtil.readString(buffer)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val message = EmuUtil.readString(buffer)
      return MessageParseResult.Success(
        if (username.isBlank()) {
          Request(messageNumber = messageNumber, message = message)
        } else {
          Notification(messageNumber = messageNumber, username = username, message = message)
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: Chat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is Request -> ""
          is Notification -> message.username
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
