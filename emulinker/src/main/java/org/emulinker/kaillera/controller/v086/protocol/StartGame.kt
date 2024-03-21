package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedByte
import org.emulinker.util.UnsignedUtil.readUnsignedShort

sealed class StartGame : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SINGLE_BYTE

  override fun writeBodyTo(buffer: ByteBuffer) {
    StartGameSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    StartGameSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x11

    private const val REQUEST_VAL1 = 0xFFFF
    private const val REQUEST_PLAYER_NUMBER = 0xFF.toShort()
    private const val REQUEST_NUM_PLAYERS = 0xFF.toShort()
  }

  object StartGameSerializer : MessageSerializer<StartGame> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<StartGame> {
      if (buffer.readableBytes() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.readByte()
      if (b.toInt() != 0x00) {
        return parseFailure("Failed byte count validation!")
      }
      val val1 = buffer.getUnsignedShort()
      val playerNumber = buffer.getUnsignedByte()
      val numPlayers = buffer.getUnsignedByte()
      return Result.success(
        if (
          val1 == REQUEST_VAL1 &&
            playerNumber == REQUEST_PLAYER_NUMBER &&
            numPlayers == REQUEST_NUM_PLAYERS
        )
          StartGameRequest(messageNumber)
        else StartGameNotification(messageNumber, val1, playerNumber, numPlayers)
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<StartGame> {
      if (buffer.remaining() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        return parseFailure("Failed byte count validation!")
      }
      val val1 = buffer.getUnsignedShort()
      val playerNumber = buffer.getUnsignedByte()
      val numPlayers = buffer.getUnsignedByte()
      return Result.success(
        if (
          val1 == REQUEST_VAL1 &&
            playerNumber == REQUEST_PLAYER_NUMBER &&
            numPlayers == REQUEST_NUM_PLAYERS
        )
          StartGameRequest(messageNumber)
        else StartGameNotification(messageNumber, val1, playerNumber, numPlayers)
      )
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<StartGame> {
      if (packet.remaining < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = packet.readByte()
      if (b.toInt() != 0x00) {
        return parseFailure("Failed byte count validation!")
      }
      val val1 = packet.readUnsignedShort()
      val playerNumber = packet.readUnsignedByte()
      val numPlayers = packet.readUnsignedByte()
      return Result.success(
        if (
          val1 == REQUEST_VAL1 &&
            playerNumber == REQUEST_PLAYER_NUMBER &&
            numPlayers == REQUEST_NUM_PLAYERS
        )
          StartGameRequest(messageNumber)
        else StartGameNotification(messageNumber, val1, playerNumber, numPlayers)
      )
    }

    override fun write(buffer: ByteBuf, message: StartGame) {
      buffer.writeByte(0x00)
      buffer.putUnsignedShort(
        when (message) {
          is StartGameRequest -> REQUEST_VAL1
          is StartGameNotification -> message.val1
        }
      )
      buffer.putUnsignedByte(
        when (message) {
          is StartGameRequest -> REQUEST_PLAYER_NUMBER.toInt()
          is StartGameNotification -> message.playerNumber.toInt()
        }
      )
      buffer.putUnsignedByte(
        when (message) {
          is StartGameRequest -> REQUEST_NUM_PLAYERS.toInt()
          is StartGameNotification -> message.numPlayers.toInt()
        }
      )
    }

    override fun write(buffer: ByteBuffer, message: StartGame) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(
        when (message) {
          is StartGameRequest -> REQUEST_VAL1
          is StartGameNotification -> message.val1
        }
      )
      buffer.putUnsignedByte(
        when (message) {
          is StartGameRequest -> REQUEST_PLAYER_NUMBER.toInt()
          is StartGameNotification -> message.playerNumber.toInt()
        }
      )
      buffer.putUnsignedByte(
        when (message) {
          is StartGameRequest -> REQUEST_NUM_PLAYERS.toInt()
          is StartGameNotification -> message.numPlayers.toInt()
        }
      )
    }
  }
}

/**
 * Message sent from the server to all clients, informing that a game has started.
 *
 * Shares a message type ID with [StartGameRequest]: `0x11`.
 *
 * @param playerNumber The player that triggered the game to start.
 */
data class StartGameNotification(
  override val messageNumber: Int,
  val val1: Int,
  val playerNumber: Short,
  val numPlayers: Short
) : StartGame(), ServerMessage {

  init {
    require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
    require(playerNumber in 0..0xFF) { "playerNumber out of acceptable range: $playerNumber" }
    require(numPlayers in 0..0xFF) { "numPlayers out of acceptable range: $numPlayers" }
  }
}

/**
 * Message sent from the client to request that a game starts.
 *
 * Shares a message type ID with [StartGameNotification]: `0x11`.
 */
data class StartGameRequest(override val messageNumber: Int) : StartGame(), ClientMessage
