package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

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

  public override fun writeBodyTo(buffer: ByteBuffer) {
    InformationMessageSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x17
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
      EmuUtil.writeString(buffer, message.source)
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
