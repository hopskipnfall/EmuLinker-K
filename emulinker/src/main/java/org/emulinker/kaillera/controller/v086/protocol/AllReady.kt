package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil

/**
 * Message sent by both client and server to indicate that they are ready for the game to start.
 *
 * When the server receives [AllReady] from all clients in a room, the server responds back with
 * [AllReady] to all clients which signals the game should start.
 *
 * Message type ID: `0x15`.
 */
data class AllReady(override val messageNumber: Int) : V086Message(), ServerMessage, ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    AllReadySerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x15
  }

  object AllReadySerializer : MessageSerializer<AllReady> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<AllReady> {
      if (buffer.remaining() < 1) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }

      val b = buffer.get()
      if (b.toInt() != 0x00) {
        return MessageParseResult.Failure(
          "Invalid All Ready Signal format: byte 0 = " + EmuUtil.byteToHex(b)
        )
      }
      return MessageParseResult.Success(AllReady(messageNumber))
    }

    override fun write(buffer: ByteBuffer, message: AllReady) {
      buffer.put(0x00.toByte())
    }
  }
}
