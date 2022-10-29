package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

data class GameKick
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val userId: Int) : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.SHORT

  init {
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
  }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedShort(userId)
  }

  companion object {
    const val ID: Byte = 0x0F

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<GameKick> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      /*SF MOD
      if (b != 0x00)
      	throw new MessageFormatException("Invalid " + DESC + " format: byte 0 = " + EmuUtil.byteToHex(b));
      */
      return MessageParseResult.Success(GameKick(messageNumber, buffer.getUnsignedShort()))
    }

    object GameKickSerializer : MessageSerializer<GameKick> {
      override val messageTypeId: Byte = ID

      override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<GameKick> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: GameKick) {
        TODO("Not yet implemented")
      }
    }
  }
}
