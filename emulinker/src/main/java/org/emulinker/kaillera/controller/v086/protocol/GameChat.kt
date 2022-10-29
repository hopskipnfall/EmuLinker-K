package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

sealed class GameChat : V086Message() {
  abstract val username: String
  abstract val message: String

  override val bodyBytes: Int
    get() = username.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    EmuUtil.writeString(buffer, message)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val message: String
  ) : GameChat() {
    override val messageTypeId = ID
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int, override val message: String) : GameChat() {
    override val messageTypeId = ID
    override val username = ""
  }

  companion object {
    const val ID: Byte = 0x08

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<GameChat> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val message = EmuUtil.readString(buffer)
      return MessageParseResult.Success(
        if (userName.isBlank()) {
          Request(messageNumber, message)
        } else {
          Notification(messageNumber, userName, message)
        }
      )
    }

    object GameChatNotificationSerializer : MessageSerializer<GameChat.Notification> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<GameChat.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: GameChat.Notification) {
        TODO("Not yet implemented")
      }
    }

    object GameChatRequestSerializer : MessageSerializer<GameChat.Request> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<GameChat.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: GameChat.Request) {
        TODO("Not yet implemented")
      }
    }
  }
}
