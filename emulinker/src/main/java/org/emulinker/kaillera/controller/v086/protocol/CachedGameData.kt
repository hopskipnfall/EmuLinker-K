package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedByte

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

  public override fun writeBodyTo(buffer: ByteBuffer) {
    CachedGameDataSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x13
  }

  object CachedGameDataSerializer : MessageSerializer<CachedGameData> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<CachedGameData> {
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      // removed to increase speed
      // if (b != 0x00)
      // throw new MessageFormatException("Invalid " + DESC + " format: byte 0 = " +
      // EmuUtil.byteToHex(b));
      return MessageParseResult.Success(
        CachedGameData(messageNumber, buffer.getUnsignedByte().toInt())
      )
    }

    override fun write(buffer: ByteBuffer, message: CachedGameData) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedByte(message.key)
    }
  }
}
