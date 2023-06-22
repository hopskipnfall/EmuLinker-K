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
        is GameChatRequest -> REQUEST_USERNAME
        is GameChatNotification -> this.username
      }.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  public override fun writeBodyTo(buffer: ByteBuffer) {
    GameChatSerializer.write(buffer, this)
  }

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
          GameChatRequest(messageNumber, message)
        } else {
          GameChatNotification(messageNumber, userName, message)
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: GameChat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is GameChatRequest -> REQUEST_USERNAME
          is GameChatNotification -> message.username
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}

/**
 * Message sent by the server containing a game chat message.
 *
 * This shares a message type ID with [GameChatRequest]: `0x08`.
 */
data class GameChatNotification
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val username: String, override val message: String) :
  GameChat(), ServerMessage

/**
 * Message sent by the client containing a game chat message.
 *
 * This shares a message type ID with [GameChatNotification]: `0x08`.
 */
data class GameChatRequest
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, override val message: String) :
  GameChat(), ClientMessage
