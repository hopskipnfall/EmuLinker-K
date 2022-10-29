package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class QuitGame : V086Message() {
  override val messageTypeId = ID

  public override fun writeBodyTo(buffer: ByteBuffer) {
    QuitGameSerializer.write(buffer, this)
  }

  override val bodyBytes: Int
    get() =
      when (this) {
        is Request -> REQUEST_USERNAME
        is Notification -> this.username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT

  data class Request constructor(override val messageNumber: Int) : QuitGame()

  data class Notification
  constructor(override val messageNumber: Int, val username: String, val userId: Int) : QuitGame() {

    init {
      require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    }
  }

  companion object {
    const val ID: Byte = 0x0B

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_USER_ID = 0xFFFF
  }

  object QuitGameSerializer : MessageSerializer<QuitGame> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<QuitGame> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      return MessageParseResult.Success(
        if (userName == REQUEST_USERNAME && userID == REQUEST_USER_ID) {
          Request(messageNumber)
        } else {
          Notification(messageNumber, userName, userID)
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: QuitGame) {
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
    }
  }
}
