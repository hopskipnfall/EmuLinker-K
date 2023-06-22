package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

data class CloseGame(
  override val messageNumber: Int,
  val gameId: Int,
  // TODO(nue): Figure out what [val1] represents..
  val val1: Int
) : V086Message(), ServerMessage {

  override val messageTypeId = ID

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.SHORT + V086Utils.Bytes.SHORT

  public override fun writeBodyTo(buffer: ByteBuffer) {
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

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<CloseGame> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00)
        throw MessageFormatException("Invalid Close Game format: byte 0 = " + EmuUtil.byteToHex(b))
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      return MessageParseResult.Success(CloseGame(messageNumber, gameID, val1))
    }

    override fun write(buffer: ByteBuffer, message: CloseGame) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(message.val1)
    }
  }
}
