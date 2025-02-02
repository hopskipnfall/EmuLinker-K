package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.remaining
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlinx.io.Source
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString

sealed class GameChat : V086Message() {
  abstract val message: String
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is GameChatRequest -> REQUEST_USERNAME
        is GameChatNotification -> this.username
      }.getNumBytesPlusStopByte() + message.getNumBytesPlusStopByte()

  override fun writeBodyTo(buffer: ByteBuffer) {
    GameChatSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    GameChatSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x08

    const val REQUEST_USERNAME = ""
  }

  object GameChatSerializer : MessageSerializer<GameChat> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<GameChat> {
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = buffer.readString()
      return Result.success(
        if (userName == REQUEST_USERNAME) {
          GameChatRequest(messageNumber, message)
        } else {
          GameChatNotification(messageNumber, userName, message)
        }
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<GameChat> {
      if (buffer.remaining() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = buffer.readString()
      return Result.success(
        if (userName == REQUEST_USERNAME) {
          GameChatRequest(messageNumber, message)
        } else {
          GameChatNotification(messageNumber, userName, message)
        }
      )
    }

    override fun read(packet: Source, messageNumber: Int): Result<GameChat> {
      if (packet.remaining < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = packet.readString()
      if (packet.remaining < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val message = packet.readString()
      return Result.success(
        if (userName == REQUEST_USERNAME) {
          GameChatRequest(messageNumber, message)
        } else {
          GameChatNotification(messageNumber, userName, message)
        }
      )
    }

    override fun write(buffer: ByteBuf, message: GameChat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is GameChatRequest -> REQUEST_USERNAME
          is GameChatNotification -> message.username
        },
      )
      EmuUtil.writeString(buffer, message.message)
    }

    override fun write(buffer: ByteBuffer, message: GameChat) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is GameChatRequest -> REQUEST_USERNAME
          is GameChatNotification -> message.username
        },
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
