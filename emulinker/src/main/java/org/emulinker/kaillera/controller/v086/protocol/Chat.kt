package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil

abstract class Chat : V086Message() {
  abstract val username: String
  abstract val message: String

  override val bodyLength: Int
    get() = username.getNumBytes() + message.getNumBytes() + 2

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
    EmuUtil.writeString(buffer, message, 0x00, AppModule.charsetDoNotUse)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val message: String
  ) : Chat() {
    override val messageId = ID
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val message: String,
    override val username: String
  ) : Chat() {
    override val messageId = ID
  }

  companion object {
    const val ID: Byte = 0x07
    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<Chat> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val message = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      return MessageParseResult.Success(
        if (userName.isBlank()) {
          Request(messageNumber, message, username = "")
        } else {
          Notification(messageNumber, userName, message)
        }
      )
    }
  }
}
