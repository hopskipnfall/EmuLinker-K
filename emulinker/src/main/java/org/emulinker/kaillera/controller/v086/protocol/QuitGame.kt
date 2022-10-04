package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

abstract class QuitGame : V086Message() {
  abstract val username: String
  abstract val userId: Int

  override val messageId = ID

  override val bodyLength: Int
    get() = username.getNumBytes() + 3

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
    buffer.putUnsignedShort(userId)
  }

  companion object {
    const val ID: Byte = 0x0B

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): QuitGame {
      if (buffer.remaining() < 3) throw ParseException("Failed byte count validation!")
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      if (buffer.remaining() < 2) throw ParseException("Failed byte count validation!")
      val userID = buffer.getUnsignedShort()
      return if (userName.isBlank() && userID == 0xFFFF) {
        QuitGame_Request(messageNumber)
      } else {
        QuitGame_Notification(messageNumber, userName, userID)
      }
    }
  }
}
