package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedShort

data class CloseGame(
  override val messageNumber: Int,
  val gameId: Int,
  // TODO(nue): Figure out what [val1] represents..
  val val1: Int
) : V086Message(), ServerMessage {

  override val messageTypeId = ID

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.SHORT + V086Utils.Bytes.SHORT

  override fun writeBodyTo(buffer: ByteBuffer) {
    CloseGameSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    CloseGameSerializer.write(buffer, this)
  }

  init {
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
  }

  companion object {
    const val ID: Byte = 0x10
  }

  object CloseGameSerializer : MessageSerializer<CloseGame> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<CloseGame> {
      if (buffer.readableBytes() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.readByte()
      if (b.toInt() != 0x00)
        throw MessageFormatException("Invalid Close Game format: byte 0 = " + EmuUtil.byteToHex(b))
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      return Result.success(CloseGame(messageNumber, gameID, val1))
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<CloseGame> {
      if (buffer.remaining() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00)
        throw MessageFormatException("Invalid Close Game format: byte 0 = " + EmuUtil.byteToHex(b))
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      return Result.success(CloseGame(messageNumber, gameID, val1))
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<CloseGame> {
      if (packet.remaining < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = packet.readByte()
      if (b.toInt() != 0x00)
        throw MessageFormatException("Invalid Close Game format: byte 0 = " + EmuUtil.byteToHex(b))
      val gameID = packet.readUnsignedShort()
      val val1 = packet.readUnsignedShort()
      return Result.success(CloseGame(messageNumber, gameID, val1))
    }

    override fun write(buffer: ByteBuf, message: CloseGame) {
      buffer.writeByte(0x00)
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(message.val1)
    }

    override fun write(buffer: ByteBuffer, message: CloseGame) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(message.val1)
    }
  }
}
