package org.emulinker.kaillera.controller.v086.protocol

import io.github.hopskipnfall.kaillera.protocol.model.ConnectionType
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString

/**
 * Message sent from the client when a user joins the server to indicate [username], [clientType],
 * and [connectionType].
 *
 * Message type ID: `0x03`.
 */
data class UserInformation
@Throws(MessageFormatException::class)
constructor(
  override var messageNumber: Int,
  val username: String,
  val clientType: String,
  val connectionType: ConnectionType,
) : V086Message(), ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes: Int =
    username.getNumBytesPlusStopByte() +
      clientType.getNumBytesPlusStopByte() +
      V086Utils.Bytes.SINGLE_BYTE

  override fun writeBodyTo(buffer: ByteBuffer) {
    UserInformationSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    UserInformationSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x03
  }

  object UserInformationSerializer : MessageSerializer<UserInformation> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<UserInformation> {
      if (buffer.readableBytes() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val clientType = buffer.readString()
      if (buffer.readableBytes() < 1) {
        return parseFailure("Failed byte count validation!")
      }
      val connectionType = buffer.readByte()
      return Result.success(
        UserInformation(
          messageNumber,
          userName,
          clientType,
          ConnectionType.fromByteValue(connectionType),
        )
      )
    }

    override fun write(buffer: ByteBuf, message: UserInformation) {
      EmuUtil.writeString(buffer, message.username)
      EmuUtil.writeString(buffer, message.clientType)
      buffer.writeByte(message.connectionType.byteValue.toInt())
    }

    fun write(buffer: ByteBuffer, message: UserInformation) {
      EmuUtil.writeString(buffer, message.username)
      EmuUtil.writeString(buffer, message.clientType)
      buffer.put(message.connectionType.byteValue)
    }
  }
}
