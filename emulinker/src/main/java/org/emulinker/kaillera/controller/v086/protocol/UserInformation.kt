package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil

/**
 * Message sent from the client when a user joins the server to indicate [username], [clientType],
 * and [connectionType].
 *
 * Message type ID: `0x03`.
 */
data class UserInformation
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val username: String,
  val clientType: String,
  val connectionType: ConnectionType
) : V086Message(), ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes: Int =
    username.getNumBytesPlusStopByte() +
      clientType.getNumBytesPlusStopByte() +
      V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    UserInformationSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x03
  }

  object UserInformationSerializer : MessageSerializer<UserInformation> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<UserInformation> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val clientType = EmuUtil.readString(buffer)
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

    override fun write(buffer: ByteBuffer, message: UserInformation) {
      EmuUtil.writeString(buffer, message.username)
      EmuUtil.writeString(buffer, message.clientType)
      buffer.put(message.connectionType.byteValue)
    }
  }
}
