package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil

data class AllReady constructor(override val messageNumber: Int) : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    AllReadySerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x15

    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<AllReady> =
      AllReadySerializer.read(buffer, messageNumber = messageNumber)
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
