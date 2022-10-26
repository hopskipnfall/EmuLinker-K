package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.util.EmuUtil

data class AllReady
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int) : V086Message() {
  override val bodyLength = 1

  override val messageId = ID

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
  }

  companion object {
    const val ID: Byte = 0x15

    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<AllReady> {
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
  }
}
