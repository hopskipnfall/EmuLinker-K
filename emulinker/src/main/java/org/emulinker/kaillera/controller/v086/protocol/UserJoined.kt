package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

data class UserJoined
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val username: String,
  val userId: Int,
  val ping: Long,
  val connectionType: ConnectionType
) : V086Message() {

  override val messageId = ID

  init {
    if (username.isBlank()) throw MessageFormatException("Empty username: $username")
    require(userId in 0..65535) { "UserID out of acceptable range: $userId" }
    require(ping in 0..2048) { "Ping out of acceptable range: $ping" }
  }

  override val bodyLength = username.getNumBytes() + 8

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
    buffer.putUnsignedShort(userId)
    buffer.putUnsignedInt(ping)
    buffer.put(connectionType.byteValue)
  }

  companion object {
    const val ID: Byte = 0x02

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<UserJoined> {
      if (buffer.remaining() < 9) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      if (buffer.remaining() < 7) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      val ping = buffer.getUnsignedInt()
      val connectionType = buffer.get()
      return MessageParseResult.Success(
        UserJoined(
          messageNumber,
          userName,
          userID,
          ping,
          ConnectionType.fromByteValue(connectionType)
        )
      )
    }
  }
}
