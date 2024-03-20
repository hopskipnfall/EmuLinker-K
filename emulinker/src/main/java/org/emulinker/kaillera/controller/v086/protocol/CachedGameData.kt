package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedByte
import org.emulinker.util.UnsignedUtil.readUnsignedByte

/**
 * Fills the same function as [GameData] but encodes a 0-based cache index ([key]) corresponding to
 * game data that has already been sent.
 *
 * This message is sent by both the server and client.
 *
 * Message type ID: `0x13`.
 */
data class CachedGameData
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val key: Int) :
  V086Message(), ServerMessage, ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.SINGLE_BYTE

  override fun writeBodyTo(buffer: ByteBuffer) {
    CachedGameDataSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    CachedGameDataSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x13
  }

  object CachedGameDataSerializer : MessageSerializer<CachedGameData> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<CachedGameData> {
      if (buffer.remaining() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.get()
      // removed to increase speed
      // if (b != 0x00)
      // throw new MessageFormatException("Invalid " + DESC + " format: byte 0 = " +
      // EmuUtil.byteToHex(b));
      return Result.success(CachedGameData(messageNumber, buffer.getUnsignedByte().toInt()))
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<CachedGameData> {
      if (packet.remaining < 2) {
        return parseFailure("Failed byte count validation!")
      }
      packet.readByte() // Move forward one byte (it's probably 0x00).
      // removed to increase speed
      // if (b != 0x00)
      // throw new MessageFormatException("Invalid " + DESC + " format: byte 0 = " +
      // EmuUtil.byteToHex(b));
      return Result.success(CachedGameData(messageNumber, packet.readUnsignedByte().toInt()))
    }

    override fun write(buffer: ByteBuf, message: CachedGameData) {
      buffer.writeByte(0x00)
      buffer.putUnsignedByte(message.key)
    }

    override fun write(buffer: ByteBuffer, message: CachedGameData) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedByte(message.key)
    }
  }
}
