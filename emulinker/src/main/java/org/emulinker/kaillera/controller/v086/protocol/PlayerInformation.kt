package org.emulinker.kaillera.controller.v086.protocol

import io.github.hopskipnfall.kaillera.protocol.model.ConnectionType
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.EmuUtil.toMillisDouble
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent by the server when a user joins a game, which lists all of the players in that game.
 *
 * Message type ID: `0x0D`.
 */
data class PlayerInformation(override var messageNumber: Int, val players: List<Player>) :
  V086Message(), ServerMessage {
  override val messageTypeId = ID

  val numPlayers: Int = players.size

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.INTEGER + players.sumOf { it.numBytes }

  override fun writeBodyTo(buffer: ByteBuffer) {
    PlayerInformationSerializer.write(buffer, this)
  }

  override fun writeBodyTo(buffer: ByteBuf) {
    PlayerInformationSerializer.write(buffer, this)
  }

  data class Player(
    val username: String,
    val ping: Duration,
    val userId: Int,
    val connectionType: ConnectionType,
  ) {
    val numBytes: Int =
      username.getNumBytesPlusStopByte() +
        V086Utils.Bytes.INTEGER +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE

    fun writeTo(buffer: ByteBuf) {
      EmuUtil.writeString(buffer, username)
      buffer.writeIntLE(ping.toMillisDouble().roundToInt())
      buffer.writeShortLE(userId)
      buffer.writeByte(connectionType.byteValue.toInt())
    }

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedInt(ping.toMillisDouble().roundToLong())
      buffer.putUnsignedShort(userId)
      buffer.put(connectionType.byteValue)
    }

    init {
      if (ping !in 0.milliseconds..2048.milliseconds) {
        throw MessageFormatException(
          "Invalid Player Information format: ping out of acceptable range: $ping"
        )
      }
      if (userId !in 0..65535) {
        throw MessageFormatException(
          "Invalid Player Information format: userID out of acceptable range: $userId"
        )
      }
    }
  }

  companion object {
    const val ID: Byte = 0x0D
  }

  object PlayerInformationSerializer : MessageSerializer<PlayerInformation> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuf, messageNumber: Int): Result<PlayerInformation> {
      if (buffer.readableBytes() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.readByte()
      if (b.toInt() != 0x00) {
        throw MessageFormatException(
          "Invalid Player Information format: byte 0 = ${b.toHexString()}"
        )
      }
      val numPlayers = buffer.readIntLE()
      val minLen = numPlayers * 9
      if (buffer.readableBytes() < minLen) {
        return parseFailure("Failed byte count validation!")
      }
      val players: List<Player> =
        (0 until numPlayers).map {
          if (buffer.readableBytes() < 9) {
            return parseFailure("Failed byte count validation!")
          }
          val userName = buffer.readString()
          if (buffer.readableBytes() < 7) {
            return parseFailure("Failed byte count validation!")
          }
          val ping = buffer.readIntLE()
          val userID = buffer.readShortLE().toInt()
          val connectionType = buffer.readByte()
          Player(userName, ping.milliseconds, userID, ConnectionType.fromByteValue(connectionType))
        }
      return Result.success(PlayerInformation(messageNumber, players))
    }

    override fun write(buffer: ByteBuf, message: PlayerInformation) {
      buffer.writeByte(0x00)
      buffer.writeIntLE(message.players.size)
      message.players.forEach { it.writeTo(buffer) }
    }

    fun write(buffer: ByteBuffer, message: PlayerInformation) {
      buffer.put(0x00.toByte())
      buffer.putInt(message.players.size)
      message.players.forEach { it.writeTo(buffer) }
    }
  }
}
