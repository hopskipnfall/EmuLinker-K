package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

data class InformationMessage
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val source: String, val message: String) :
  V086Message() {

  override val messageTypeId = ID

  init {
    require(source.isNotBlank()) { "source cannot be blank" }
    require(message.isNotBlank()) { "message cannot be blank" }
  }

  override val bodyBytes = source.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, source)
    EmuUtil.writeString(buffer, message)
  }

  companion object {
    const val ID: Byte = 0x17

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<InformationMessage> {
      return InformationMessageSerializer.read(buffer, messageNumber)
    }

    object InformationMessageSerializer : MessageSerializer<InformationMessage> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<InformationMessage> {
        if (buffer.remaining() < 4) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val source = EmuUtil.readString(buffer)
        if (buffer.remaining() < 2) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val message = EmuUtil.readString(buffer)
        return MessageParseResult.Success(InformationMessage(messageNumber, source, message))
      }

      override fun write(buffer: ByteBuffer, message: InformationMessage) {
        TODO("Not yet implemented")
      }
    }
  }
}
