package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil

data class UserInformation
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val username: String,
  val clientType: String,
  val connectionType: ConnectionType
) : V086Message() {

  override val messageId = ID

  override val bodyLength: Int
    get() = username.getNumBytes() + clientType.getNumBytes() + 3

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
    EmuUtil.writeString(buffer, clientType, 0x00, AppModule.charsetDoNotUse)
    buffer.put(connectionType.byteValue)
  }

  companion object {
    const val ID: Byte = 0x03

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<UserInformation> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val clientType = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      if (buffer.remaining() < 1) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val connectionType = buffer.get()
      return MessageParseResult.Success(
        UserInformation(
          messageNumber,
          userName,
          clientType,
          ConnectionType.fromByteValue(connectionType)
        )
      )
    }
  }
}
