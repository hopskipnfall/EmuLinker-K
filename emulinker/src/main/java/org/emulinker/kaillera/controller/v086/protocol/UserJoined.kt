package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.remaining
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.io.Source
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.EmuUtil.toMillisDouble
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedInt
import org.emulinker.util.UnsignedUtil.readUnsignedShort

/**
 * Message sent from the server to all clients to give information about a new client that has
 * joined the server.
 *
 * Message type ID: `0x02`.
 */
data class UserJoined(
  override val messageNumber: Int,
  val username: String,
  val userId: Int,
  val ping: Duration,
  val connectionType: ConnectionType,
) : V086Message() {
  override val messageTypeId = ID

  init {
    if (username.isBlank()) throw MessageFormatException("Empty username: $username")
    require(userId in 0..65535) { "UserID out of acceptable range: $userId" }
    require(ping in 0.milliseconds..2048.milliseconds) { "Ping out of acceptable range: $ping" }
  }

  override val bodyBytes =
    username.getNumBytesPlusStopByte() +
      V086Utils.Bytes.SHORT +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.SINGLE_BYTE

  override fun writeBodyTo(buffer: ByteBuffer) {
    UserJoinedSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    UserJoinedSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x02
  }

  object UserJoinedSerializer : MessageSerializer<UserJoined> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<UserJoined> {
      if (buffer.readableBytes() < 9) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 7) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      val ping = buffer.getUnsignedInt()
      val connectionType = buffer.readByte()
      return Result.success(
        UserJoined(
          messageNumber,
          userName,
          userID,
          ping.milliseconds,
          ConnectionType.fromByteValue(connectionType),
        )
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<UserJoined> {
      if (buffer.remaining() < 9) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 7) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      val ping = buffer.getUnsignedInt()
      val connectionType = buffer.get()
      return Result.success(
        UserJoined(
          messageNumber,
          userName,
          userID,
          ping.milliseconds,
          ConnectionType.fromByteValue(connectionType),
        )
      )
    }

    override fun read(packet: Source, messageNumber: Int): Result<UserJoined> {
      if (packet.remaining < 9) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = packet.readString()
      if (packet.remaining < 7) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = packet.readUnsignedShort()
      val ping = packet.readUnsignedInt()
      val connectionType = packet.readByte()
      return Result.success(
        UserJoined(
          messageNumber,
          userName,
          userID,
          ping.milliseconds,
          ConnectionType.fromByteValue(connectionType),
        )
      )
    }

    override fun write(buffer: ByteBuf, message: UserJoined) {
      EmuUtil.writeString(buffer, message.username)
      buffer.putUnsignedShort(message.userId)
      buffer.putUnsignedInt(message.ping.toMillisDouble().roundToLong())
      buffer.writeByte(message.connectionType.byteValue.toInt())
    }

    override fun write(buffer: ByteBuffer, message: UserJoined) {
      EmuUtil.writeString(buffer, message.username)
      buffer.putUnsignedShort(message.userId)
      buffer.putUnsignedInt(message.ping.toMillisDouble().roundToLong())
      buffer.put(message.connectionType.byteValue)
    }
  }
}
