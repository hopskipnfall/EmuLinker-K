package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

sealed class GameChat : V086Message() {
  abstract val message: String
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is Request -> REQUEST_USERNAME
        is Notification -> this.username
      }.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    GameChatSerializer.write(buffer, this)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int, val username: String, override val message: String) :
    GameChat()

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int, override val message: String) : GameChat()

  companion object {
    const val ID: Byte = 0x08

    const val REQUEST_USERNAME = ""
  }

  object GameChatSerializer : MessageSerializer<GameChat> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<GameChat> {
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val message = EmuUtil.readString(buffer)
      return MessageParseResult.Success(
        if (userName == REQUEST_USERNAME) {
          Request(messageNumber, message)
        } else {
          Notification(messageNumber, userName, message)
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: GameChat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is Request -> REQUEST_USERNAME
          is Notification -> message.username
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}
