package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedByte

data class KeepAlive
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val value: Short) : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE

  init {
    require(value in 0..0xFF) { "val out of acceptable range: $value" }
  }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.putUnsignedByte(value.toInt())
  }

  companion object {
    const val ID: Byte = 0x09

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<KeepAlive> {
      return KeepAliveSerializer.read(buffer, messageNumber)
    }

    object KeepAliveSerializer : MessageSerializer<KeepAlive> {
      override val messageTypeId: Byte = ID

      override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<KeepAlive> {
        if (buffer.remaining() < 1) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        return MessageParseResult.Success(KeepAlive(messageNumber, buffer.getUnsignedByte()))
      }

      override fun write(buffer: ByteBuffer, message: KeepAlive) {
        TODO("Not yet implemented")
      }
    }
  }
}
