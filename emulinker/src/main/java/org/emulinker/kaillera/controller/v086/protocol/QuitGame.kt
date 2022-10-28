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
  abstract val username: String
  abstract val userId: Int

  override val messageTypeId = ID

  override val bodyBytes: Int
    get() = username.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    buffer.putUnsignedShort(userId)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val userId: Int
  ) : QuitGame() {

    init {
      require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : QuitGame() {

    override val username = ""
    override val userId = 0xFFFF
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
  }
}
