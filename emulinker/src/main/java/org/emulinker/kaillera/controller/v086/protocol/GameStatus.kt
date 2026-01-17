package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent by the server to notify all clients that a game's status has changed.
 *
 * Message type ID: `0x0E`.
 */
data class GameStatus
@Throws(MessageFormatException::class)
constructor(
  override var messageNumber: Int,
  val gameId: Int,
  val val1: Int,
  val gameStatus: org.emulinker.kaillera.model.GameStatus,
  val numPlayers: Byte,
  val maxPlayers: Byte,
) : V086Message(), ServerMessage {
  override val messageTypeId = ID

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.SHORT +
      V086Utils.Bytes.SHORT +
      V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.SINGLE_BYTE

  init {
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
    require(numPlayers in 0..0xFF) { "numPlayers out of acceptable range: $numPlayers" }
    require(maxPlayers in 0..0xFF) { "maxPlayers out of acceptable range: $maxPlayers" }
  }

  override fun writeBodyTo(buffer: ByteBuffer) {
    GameStatusSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    GameStatusSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x0E
  }

  object GameStatusSerializer : MessageSerializer<GameStatus> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<GameStatus> {
      if (buffer.readableBytes() < 8) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.readByte()
      require(b.toInt() == 0x00) {
        "Invalid Game Status format: byte 0 = " + b.toHexString(HexFormat.UpperCase)
      }
      val gameID = buffer.readShortLE().toInt()
      val val1 = buffer.readShortLE().toInt()
      val gameStatus = buffer.readByte()
      val numPlayers = buffer.readByte()
      val maxPlayers = buffer.readByte()
      return Result.success(
        GameStatus(
          messageNumber,
          gameID,
          val1,
          org.emulinker.kaillera.model.GameStatus.fromByteValue(gameStatus),
          numPlayers,
          maxPlayers,
        )
      )
    }

    override fun write(buffer: ByteBuf, message: GameStatus) {
      buffer.writeByte(0x00)
      // Note: I think val0 is always 00, meaning that this could just be
      // buffer.writeIntLE(message.gameId) removing val1 entirely.
      buffer.writeShortLE(message.gameId)
      buffer.writeShortLE(message.val1)
      buffer.writeByte(message.gameStatus.byteValue.toInt())
      buffer.writeByte(message.numPlayers.toInt())
      buffer.writeByte(message.maxPlayers.toInt())
    }

    fun write(buffer: ByteBuffer, message: GameStatus) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(message.val1)
      buffer.put(message.gameStatus.byteValue)
      buffer.put(message.numPlayers)
      buffer.put(message.maxPlayers)
    }
  }
}
