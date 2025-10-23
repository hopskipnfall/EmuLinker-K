package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString

sealed class PlayerDrop : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is PlayerDropRequest -> REQUEST_USERNAME
        is PlayerDropNotification -> username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SINGLE_BYTE

  override fun writeBodyTo(buffer: ByteBuffer) {
    PlayerDropSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    PlayerDropSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x14

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_PLAYER_NUMBER = 0.toByte()
  }

  object PlayerDropSerializer : MessageSerializer<PlayerDrop> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<PlayerDrop> {
      if (buffer.readableBytes() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      val playerNumber = buffer.readByte()
      return Result.success(
        if (userName == REQUEST_USERNAME && playerNumber == REQUEST_PLAYER_NUMBER) {
          PlayerDropRequest(messageNumber)
        } else PlayerDropNotification(messageNumber, userName, playerNumber)
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<PlayerDrop> {
      if (buffer.remaining() < 2) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      val playerNumber = buffer.get()
      return Result.success(
        if (userName == REQUEST_USERNAME && playerNumber == REQUEST_PLAYER_NUMBER) {
          PlayerDropRequest(messageNumber)
        } else PlayerDropNotification(messageNumber, userName, playerNumber)
      )
    }

    override fun write(buffer: ByteBuf, message: PlayerDrop) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is PlayerDropRequest -> REQUEST_USERNAME
          is PlayerDropNotification -> message.username
        },
      )
      buffer.writeByte(
        when (message) {
          is PlayerDropRequest -> REQUEST_PLAYER_NUMBER
          is PlayerDropNotification -> message.playerNumber
        }.toInt()
      )
    }

    override fun write(buffer: ByteBuffer, message: PlayerDrop) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is PlayerDropRequest -> REQUEST_USERNAME
          is PlayerDropNotification -> message.username
        },
      )
      buffer.put(
        when (message) {
          is PlayerDropRequest -> REQUEST_PLAYER_NUMBER
          is PlayerDropNotification -> message.playerNumber
        }
      )
    }
  }
}

data class PlayerDropNotification(
  override var messageNumber: Int,
  val username: String,
  /** The port number, not the player ID. */
  val playerNumber: Byte,
) : PlayerDrop(), ServerMessage {

  init {
    require(playerNumber in 0..255) { "playerNumber out of acceptable range: $playerNumber" }
    require(username.isNotBlank()) { "Username cannot be blank" }
  }
}

data class PlayerDropRequest(override var messageNumber: Int) : PlayerDrop(), ClientMessage
