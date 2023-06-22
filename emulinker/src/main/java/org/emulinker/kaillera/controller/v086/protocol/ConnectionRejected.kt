package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent from the server to indicate that the client is not allowed to join the server,
 * including a [message] to be displayed to the user.
 *
 * Message type ID: `0x16`.
 */
data class ConnectionRejected
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val username: String,
  val userId: Int,
  val message: String
) : V086Message(), ServerMessage {

  override val messageTypeId = ID

  override val bodyBytes =
    username.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    ConnectionRejectedSerializer.write(buffer, this)
  }

  init {
    require(username.isNotBlank()) { "Username cannot be empty" }
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    require(message.isNotBlank()) { "Message cannot be empty" }
  }

  companion object {
    const val ID: Byte = 0x16
  }

  object ConnectionRejectedSerializer : MessageSerializer<ConnectionRejected> {
    override val messageTypeId: Byte = ID

    override fun read(
      buffer: ByteBuffer,
      messageNumber: Int
    ): MessageParseResult<ConnectionRejected> {
      if (buffer.remaining() < 6) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 4) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }

      val message = EmuUtil.readString(buffer)
      return MessageParseResult.Success(
        ConnectionRejected(messageNumber, userName, userID, message)
      )
    }

    override fun write(buffer: ByteBuffer, message: ConnectionRejected) {
      EmuUtil.writeString(buffer, message.username)
      buffer.putUnsignedShort(message.userId)
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
