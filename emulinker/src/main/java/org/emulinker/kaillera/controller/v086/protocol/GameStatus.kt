package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

data class GameStatus
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val gameId: Int,
  val val1: Int,
  val gameStatus: org.emulinker.kaillera.model.GameStatus,
  val numPlayers: Byte,
  val maxPlayers: Byte
) : V086Message() {

  override val messageId = ID

  override val bodyLength = 8

  init {
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
    require(numPlayers in 0..0xFF) { "numPlayers out of acceptable range: $numPlayers" }
    require(maxPlayers in 0..0xFF) { "maxPlayers out of acceptable range: $maxPlayers" }
  }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedShort(gameId)
    buffer.putUnsignedShort(val1)
    buffer.put(gameStatus.byteValue)
    buffer.put(numPlayers)
    buffer.put(maxPlayers)
  }

  companion object {
    const val ID: Byte = 0x0E

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<GameStatus> {
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
  }
}
