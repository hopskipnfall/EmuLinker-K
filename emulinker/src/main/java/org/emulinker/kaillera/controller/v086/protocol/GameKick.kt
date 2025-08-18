package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.remaining
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent to kick a user by [userId] from a game.
 *
 * Message type ID: `0x0F`.
 */
data class GameKick
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val userId: Int) : V086Message(), ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.SHORT

  init {
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
  }

  override fun writeBodyTo(buffer: ByteBuffer) {
    GameKickSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    GameKickSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x0F
  }

  object GameKickSerializer : MessageSerializer<GameKick> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<GameKick> {
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      buffer.readByte() // Skip over 0x00 byte.
      return Result.success(GameKick(messageNumber, buffer.getUnsignedShort()))
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<GameKick> {
      if (buffer.remaining() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      buffer.get() // This is always 0x00.
      return Result.success(GameKick(messageNumber, buffer.getUnsignedShort()))
    }

    override fun write(buffer: ByteBuf, message: GameKick) {
      buffer.writeByte(0x00)
      buffer.putUnsignedShort(message.userId)
    }

    override fun write(buffer: ByteBuffer, message: GameKick) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.userId)
    }
  }
}
