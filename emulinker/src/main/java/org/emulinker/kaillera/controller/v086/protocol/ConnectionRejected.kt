package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
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
  override var messageNumber: Int,
  val username: String,
  val userId: Int,
  val message: String,
) : V086Message(), ServerMessage {

  override val messageTypeId = ID

  override val bodyBytes =
    username.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT + message.getNumBytesPlusStopByte()

  override fun writeBodyTo(buffer: ByteBuffer) {
    ConnectionRejectedSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
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

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<ConnectionRejected> {
      if (buffer.readableBytes() < 6) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 4) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.readShortLE()
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }

      val message = buffer.readString()
      return Result.success(ConnectionRejected(messageNumber, userName, userID.toInt(), message))
    }

    override fun write(buffer: ByteBuf, message: ConnectionRejected) {
      EmuUtil.writeString(buffer, message.username)
      buffer.writeShortLE(message.userId)
      EmuUtil.writeString(buffer, message.message)
    }

    fun write(buffer: ByteBuffer, message: ConnectionRejected) {
      EmuUtil.writeString(buffer, message.username)
      buffer.putUnsignedShort(message.userId)
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
