package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString

/**
 * A message from the server containing text to be displayed to the user, including a source.
 *
 * Message type ID: `0x17`.
 */
data class InformationMessage
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val source: String, val message: String) :
  V086Message(), ServerMessage {

  override val messageTypeId = ID

  init {
    require(source.isNotBlank()) { "source cannot be blank" }
    require(message.isNotBlank()) { "message cannot be blank" }
  }

  override val bodyBytes = source.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  override fun writeBodyTo(buffer: ByteBuffer) {
    InformationMessageSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    InformationMessageSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x17
  }

  object InformationMessageSerializer : MessageSerializer<InformationMessage> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<InformationMessage> {
      if (buffer.readableBytes() < 4) {
        return parseFailure("Failed byte count validation!")
      }
      val source = buffer.readString()
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = buffer.readString()
      return Result.success(InformationMessage(messageNumber, source, message))
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<InformationMessage> {
      if (buffer.remaining() < 4) {
        return parseFailure("Failed byte count validation!")
      }
      val source = buffer.readString()
      if (buffer.remaining() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = buffer.readString()
      return Result.success(InformationMessage(messageNumber, source, message))
    }

    override fun write(buffer: ByteBuf, message: InformationMessage) {
      EmuUtil.writeString(buffer, message.source)
      EmuUtil.writeString(buffer, message.message)
    }

    override fun write(buffer: ByteBuffer, message: InformationMessage) {
      EmuUtil.writeString(buffer, message.source)
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
