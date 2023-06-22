package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedByte

/**
 * Message periodically sent by the client so the server knows it is still connected on that port.
 *
 * Message type ID: `0x09`.
 */
data class KeepAlive
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val value: Short) : V086Message(), ClientMessage {
  override val messageTypeId = ID

  override val bodyBytes = V086Utils.Bytes.SINGLE_BYTE

  init {
    require(value in 0..0xFF) { "val out of acceptable range: $value" }
  }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    KeepAliveSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x09
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
      buffer.putUnsignedByte(message.value.toInt())
    }
  }
}
