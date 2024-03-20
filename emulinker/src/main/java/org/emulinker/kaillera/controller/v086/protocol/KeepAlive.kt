package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedByte
import org.emulinker.util.UnsignedUtil.readUnsignedByte

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

  override fun writeBodyTo(buffer: ByteBuffer) {
    KeepAliveSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    KeepAliveSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x09
  }

  object KeepAliveSerializer : MessageSerializer<KeepAlive> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<KeepAlive> {
      if (buffer.readableBytes() < 1) {
        return parseFailure("Failed byte count validation!")
      }
      return Result.success(KeepAlive(messageNumber, buffer.getUnsignedByte()))
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<KeepAlive> {
      if (buffer.remaining() < 1) {
        return parseFailure("Failed byte count validation!")
      }
      return Result.success(KeepAlive(messageNumber, buffer.getUnsignedByte()))
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<KeepAlive> {
      if (packet.remaining < 1) {
        return parseFailure("Failed byte count validation!")
      }
      return Result.success(KeepAlive(messageNumber, packet.readUnsignedByte()))
    }

    override fun write(buffer: ByteBuf, message: KeepAlive) {
      buffer.putUnsignedByte(message.value.toInt())
    }

    override fun write(buffer: ByteBuffer, message: KeepAlive) {
      buffer.putUnsignedByte(message.value.toInt())
    }
  }
}
