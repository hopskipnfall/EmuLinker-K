package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedShort

sealed class QuitGame : V086Message() {
  override val messageTypeId = ID

  override fun writeBodyTo(buffer: ByteBuffer) {
    QuitGameSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    QuitGameSerializer.write(buffer, this)
  }

  override val bodyBytes: Int
    get() =
      when (this) {
        is QuitGameRequest -> REQUEST_USERNAME
        is QuitGameNotification -> this.username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT

  companion object {
    const val ID: Byte = 0x0B

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_USER_ID = 0xFFFF
  }

  object QuitGameSerializer : MessageSerializer<QuitGame> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<QuitGame> {
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      return Result.success(
        if (userName == REQUEST_USERNAME && userID == REQUEST_USER_ID) {
          QuitGameRequest(messageNumber)
        } else {
          QuitGameNotification(messageNumber, userName, userID)
        }
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<QuitGame> {
      if (buffer.remaining() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      return Result.success(
        if (userName == REQUEST_USERNAME && userID == REQUEST_USER_ID) {
          QuitGameRequest(messageNumber)
        } else {
          QuitGameNotification(messageNumber, userName, userID)
        }
      )
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<QuitGame> {
      if (packet.remaining < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = packet.readString()
      if (packet.remaining < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = packet.readUnsignedShort()
      return Result.success(
        if (userName == REQUEST_USERNAME && userID == REQUEST_USER_ID) {
          QuitGameRequest(messageNumber)
        } else {
          QuitGameNotification(messageNumber, userName, userID)
        }
      )
    }

    override fun write(buffer: ByteBuf, message: QuitGame) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is QuitGameRequest -> REQUEST_USERNAME
          is QuitGameNotification -> message.username
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is QuitGameRequest -> REQUEST_USER_ID
          is QuitGameNotification -> message.userId
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: QuitGame) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is QuitGameRequest -> REQUEST_USERNAME
          is QuitGameNotification -> message.username
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is QuitGameRequest -> REQUEST_USER_ID
          is QuitGameNotification -> message.userId
        }
      )
    }
  }
}

/**
 * Message sent by the client to request to leave a game.
 *
 * Shares a message type ID with [QuitGameNotification]: `0x0B`.
 */
data class QuitGameRequest(override val messageNumber: Int) : QuitGame(), ClientMessage

/**
 * Message sent by the server to notify that a user has left the game.
 *
 * Shares a message type ID with [QuitGameRequest]: `0x0B`.
 */
data class QuitGameNotification(
  override val messageNumber: Int,
  val username: String,
  val userId: Int
) : QuitGame(), ServerMessage {

  init {
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
  }
}
