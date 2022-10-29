package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class QuitGame : V086Message() {
  override val messageTypeId = ID

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : QuitGame() {
    private val username = ""
    private val userId = 0xFFFF

    override val bodyBytes: Int
      get() = username.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT

    public override fun writeBodyTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedShort(userId)
    }
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int, val username: String, val userId: Int) : QuitGame() {

    override val bodyBytes: Int
      get() = username.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT

    public override fun writeBodyTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedShort(userId)
    }

    init {
      require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    }
  }

  companion object {
    const val ID: Byte = 0x0B

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<QuitGame> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      return MessageParseResult.Success(
        if (userName.isBlank() && userID == 0xFFFF) {
          Request(messageNumber)
        } else {
          Notification(messageNumber, userName, userID)
        }
      )
    }

    object QuitGameRequestSerializer : MessageSerializer<QuitGame.Request> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<QuitGame.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: QuitGame.Request) {
        TODO("Not yet implemented")
      }
    }

    object QuitGameNotificationSerializer : MessageSerializer<QuitGame.Notification> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<QuitGame.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: QuitGame.Notification) {
        TODO("Not yet implemented")
      }
    }
  }
}
