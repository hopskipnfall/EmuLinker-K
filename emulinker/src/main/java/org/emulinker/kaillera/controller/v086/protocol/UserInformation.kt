package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.remaining
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlinx.io.Source
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
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
  override val messageNumber: Int,
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

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<UserInformation> {
      if (buffer.remaining() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val clientType = buffer.readString()
      if (buffer.remaining() < 1) {
        return parseFailure("Failed byte count validation!")
      }
      val connectionType = buffer.get()
      return Result.success(
        UserInformation(
          messageNumber,
          userName,
          clientType,
          ConnectionType.fromByteValue(connectionType),
        )
      )
    }

    override fun read(packet: Source, messageNumber: Int): Result<UserInformation> {
      if (packet.remaining < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = packet.readString()
      if (packet.remaining < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val clientType = packet.readString()
      if (packet.remaining < 1) {
        return parseFailure("Failed byte count validation!")
      }
      val connectionType = packet.readByte()
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

    override fun write(buffer: ByteBuffer, message: UserInformation) {
      EmuUtil.writeString(buffer, message.username)
      EmuUtil.writeString(buffer, message.clientType)
      buffer.put(message.connectionType.byteValue)
    }
  }
}
