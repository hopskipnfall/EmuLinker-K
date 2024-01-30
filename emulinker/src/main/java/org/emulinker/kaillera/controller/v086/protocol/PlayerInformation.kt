package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readInt
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedInt
import org.emulinker.util.UnsignedUtil.readUnsignedShort

/**
 * Message sent by the server when a user joins a game, which lists all of the players in that game.
 *
 * Message type ID: `0x0D`.
 */
data class PlayerInformation(override val messageNumber: Int, val players: List<Player>) :
  V086Message(), ServerMessage {
  override val messageTypeId = ID

  val numPlayers: Int = players.size

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.INTEGER + players.sumOf { it.numBytes }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    PlayerInformationSerializer.write(buffer, this)
  }

  data class Player(
    val username: String,
    val ping: Long,
    val userId: Int,
    val connectionType: ConnectionType
  ) {
    val numBytes: Int =
      username.getNumBytesPlusStopByte() +
        V086Utils.Bytes.INTEGER +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedInt(ping)
      buffer.putUnsignedShort(userId)
      buffer.put(connectionType.byteValue)
    }

    init {
      if (ping !in 0..2048) { // what should max ping be?
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

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<PlayerInformation> {
      if (buffer.remaining() < 14) {
        return parseFailure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        throw MessageFormatException(
          "Invalid Player Information format: byte 0 = ${EmuUtil.byteToHex(b)}"
        )
      }
      val numPlayers = buffer.int
      val minLen = numPlayers * 9
      if (buffer.remaining() < minLen) {
        return parseFailure("Failed byte count validation!")
      }
      val players: List<Player> =
        (0 until numPlayers).map {
          if (buffer.remaining() < 9) {
            return parseFailure("Failed byte count validation!")
          }
          val userName = buffer.readString()
          if (buffer.remaining() < 7) {
            return parseFailure("Failed byte count validation!")
          }
          val ping = buffer.getUnsignedInt()
          val userID = buffer.getUnsignedShort()
          val connectionType = buffer.get()
          Player(userName, ping, userID, ConnectionType.fromByteValue(connectionType))
        }
      return Result.success(PlayerInformation(messageNumber, players))
    }

    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<PlayerInformation> {
      if (packet.remaining < 14) {
        return parseFailure("Failed byte count validation!")
      }
      val b = packet.readByte()
      if (b.toInt() != 0x00) {
        throw MessageFormatException(
          "Invalid Player Information format: byte 0 = ${EmuUtil.byteToHex(b)}"
        )
      }
      val numPlayers = packet.readInt()
      val minLen = numPlayers * 9
      if (packet.remaining < minLen) {
        return parseFailure("Failed byte count validation!")
      }
      val players: List<Player> =
        (0 until numPlayers).map {
          if (packet.remaining < 9) {
            return parseFailure("Failed byte count validation!")
          }
          val userName = packet.readString()
          if (packet.remaining < 7) {
            return parseFailure("Failed byte count validation!")
          }
          val ping = packet.readUnsignedInt()
          val userID = packet.readUnsignedShort()
          val connectionType = packet.readByte()
          Player(userName, ping, userID, ConnectionType.fromByteValue(connectionType))
        }
      return Result.success(PlayerInformation(messageNumber, players))
    }

    override fun write(buffer: ByteBuffer, message: PlayerInformation) {
      buffer.put(0x00.toByte())
      buffer.putInt(message.players.size)
      message.players.forEach { it.writeTo(buffer) }
    }
  }
}
