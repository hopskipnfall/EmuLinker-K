package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent by the server to notify all clients that a game's status has changed.
 *
 * Message type ID: `0x0E`.
 */
data class GameStatus
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val gameId: Int,
  val val1: Int,
  val gameStatus: org.emulinker.kaillera.model.GameStatus,
  val numPlayers: Byte,
  val maxPlayers: Byte
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

  public override fun writeBodyTo(buffer: ByteBuffer) {
    GameStatusSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x0E
  }

  object GameStatusSerializer : MessageSerializer<GameStatus> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<GameStatus> {
      if (buffer.remaining() < 8) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      require(b.toInt() == 0x00) { "Invalid Game Status format: byte 0 = " + EmuUtil.byteToHex(b) }
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      val gameStatus = buffer.get()
      val numPlayers = buffer.get()
      val maxPlayers = buffer.get()
      return MessageParseResult.Success(
        GameStatus(
          messageNumber,
          gameID,
          val1,
          org.emulinker.kaillera.model.GameStatus.fromByteValue(gameStatus),
          numPlayers,
          maxPlayers
        )
      )
    }

    override fun write(buffer: ByteBuffer, message: GameStatus) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(message.val1)
      buffer.put(message.gameStatus.byteValue)
      buffer.put(message.numPlayers)
      buffer.put(message.maxPlayers)
    }
  }
}
