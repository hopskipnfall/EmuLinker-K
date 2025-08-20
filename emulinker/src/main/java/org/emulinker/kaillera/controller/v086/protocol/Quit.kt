package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class Quit : V086Message() {
  override val messageTypeId = ID

  abstract val message: String

  override val bodyBytes: Int
    get() =
      when (this) {
        is QuitRequest -> REQUEST_USERNAME
        is QuitNotification -> username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SHORT + message.getNumBytesPlusStopByte()

  override fun writeBodyTo(buffer: ByteBuffer) {
    QuitSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    QuitSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x01

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_USER_ID = 0xFFFF
  }

  object QuitSerializer : MessageSerializer<Quit> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<Quit> {
      if (buffer.readableBytes() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.readableBytes() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.readUnsignedShortLE()
      val message = buffer.readString()
      return Result.success(
        if (userName.isBlank() && userID == REQUEST_USER_ID) {
          QuitRequest(messageNumber, message)
        } else {
          QuitNotification(messageNumber, userName, userID, message)
        }
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<Quit> {
      if (buffer.remaining() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 3) {
        return parseFailure("Failed byte count validation!")
      }
      val userID = buffer.getUnsignedShort()
      val message = buffer.readString()
      return Result.success(
        if (userName.isBlank() && userID == REQUEST_USER_ID) {
          QuitRequest(messageNumber, message)
        } else {
          QuitNotification(messageNumber, userName, userID, message)
        }
      )
    }

    override fun write(buffer: ByteBuf, message: Quit) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is QuitRequest -> REQUEST_USERNAME
          is QuitNotification -> message.username
        },
      )
      buffer.writeShortLE(
        when (message) {
          is QuitRequest -> REQUEST_USER_ID
          is QuitNotification -> message.userId
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }

    override fun write(buffer: ByteBuffer, message: Quit) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is QuitRequest -> REQUEST_USERNAME
          is QuitNotification -> message.username
        },
      )
      buffer.putUnsignedShort(
        when (message) {
          is QuitRequest -> REQUEST_USER_ID
          is QuitNotification -> message.userId
        }
      )
      EmuUtil.writeString(buffer, message.message)
    }
  }
}

/**
 * Message sent by the server to notify all clients that the user left the server.
 *
 * Shares a message type ID with [QuitRequest]: `0x01`.
 */
data class QuitNotification(
  override val messageNumber: Int,
  val username: String,
  val userId: Int,
  override val message: String,
) : Quit() {

  init {
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    require(username.isNotBlank()) { "Username cannot be empty" }
  }
}

/**
 * Message sent by the client before leaving the server.
 *
 * Shares a message type ID with [QuitNotification]: `0x01`.
 */
data class QuitRequest(override val messageNumber: Int, override val message: String) : Quit()
