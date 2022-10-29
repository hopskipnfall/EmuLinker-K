package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class Quit : V086Message() {
  override val messageTypeId = ID

  abstract val message: String

  override val bodyBytes: Int
    get() =
      when (this) {
        is Request -> REQUEST_USERNAME
        is Notification -> username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    QuitSerializer.write(buffer, this)
  }

  data class Notification
  constructor(
    override val messageNumber: Int,
    val username: String,
    val userId: Int,
    override val message: String
  ) : Quit() {

    init {
      require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
      require(username.isNotBlank()) { "Username cannot be empty" }
    }
  }

  data class Request constructor(override val messageNumber: Int, override val message: String) :
    Quit()

  companion object {
    const val ID: Byte = 0x01

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_USER_ID = 0xFFFF
  }

  object QuitSerializer : MessageSerializer<Quit> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<Quit> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      val message = EmuUtil.readString(buffer)
      return MessageParseResult.Success(
        if (userName.isBlank() && userID == REQUEST_USER_ID) {
          Request(messageNumber, message)
        } else {
          Notification(messageNumber, userName, userID, message)
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: Quit) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is Request -> REQUEST_USERNAME
          is Notification -> message.username
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is Request -> REQUEST_USER_ID
          is Notification -> message.userId
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
