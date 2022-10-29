package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

abstract class Chat : V086Message() {
  abstract val username: String
  abstract val message: String

  override val bodyBytes: Int
    get() = username.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    EmuUtil.writeString(buffer, message)
  }

  data class Notification
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val message: String
  ) : Chat() {
    override val messageTypeId = ID

    companion object {
      object ChatNotificationSerializer : MessageSerializer<Chat.Notification> {
        override val messageTypeId: Byte = ID

        override fun read(
          buffer: ByteBuffer,
          messageNumber: Int
        ): MessageParseResult<Chat.Notification> {
          TODO("Not yet implemented")
        }

        override fun write(buffer: ByteBuffer, message: Chat.Notification) {
          TODO("Not yet implemented")
        }
      }
    }
  }

  data class Request constructor(override val messageNumber: Int, override val message: String) :
    Chat() {
    override val messageTypeId = ID

    // TODO(nue): Find out why chat notifications have a message but no username.
    override val username = ""
  }

  companion object {
    const val ID: Byte = 0x07

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<Chat> {
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

    object ChatRequestSerializer : MessageSerializer<Chat.Request> {
      override val messageTypeId: Byte = ID

      override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<Chat.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: Chat.Request) {
        TODO("Not yet implemented")
      }
    }
  }
}
