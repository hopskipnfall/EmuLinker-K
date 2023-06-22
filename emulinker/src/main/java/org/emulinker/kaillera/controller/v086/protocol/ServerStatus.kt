package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.UserStatus
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.toHexString
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * Message sent by the server when the user joins, listing all of the users and games.
 *
 * It's possible this is meant to be sent more often than that.
 *
 * Message type ID: `0x04`.
 */
data class ServerStatus(
  override val messageNumber: Int,
  val users: List<User>,
  val games: List<Game>
) : V086Message(), ServerMessage {

  override val messageTypeId = ID

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER +
      users.sumOf { it.numBytes } +
      games.sumOf { it.numBytes }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    ServerStatusSerializer.write(buffer, this)
  }

  // TODO(nue): this User and Game class should not be here.
  data class User(
    val username: String,
    val ping: Long,
    val status: UserStatus,
    val userId: Int,
    val connectionType: ConnectionType
  ) {

    init {
      if (ping < 0 || ping > 2048)
        throw MessageFormatException(
          "Invalid Server Status format: ping out of acceptable range: $ping"
        )
      if (userId < 0 || userId > 65535)
        throw MessageFormatException(
          "Invalid Server Status format: userID out of acceptable range: $userId"
        )
    }

    val numBytes =
      (username.getNumBytesPlusStopByte() +
        // Ping.
        V086Utils.Bytes.INTEGER +
        // Status.
        V086Utils.Bytes.SINGLE_BYTE +
        // User ID.
        V086Utils.Bytes.SHORT +
        // Connection type.
        V086Utils.Bytes.SINGLE_BYTE)

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedInt(ping)
      buffer.put(status.byteValue)
      buffer.putUnsignedShort(userId)
      buffer.put(connectionType.byteValue)
    }
  }

  data class Game(
    val romName: String,
    val gameId: Int,
    val clientType: String,
    val username: String,
    /**
     * Formatted like "2/4", showing the number of players present out of the max allowed in the
     * room.
     */
    val playerCountOutOfMax: String,
    val status: GameStatus
  ) {

    init {
      require(romName.isNotBlank()) { "romName cannot be blank" }
      require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
      require(clientType.isNotBlank()) { "clientType cannot be blank" }
      require(username.isNotBlank()) { "username cannot be blank" }
    }

    val numBytes: Int
      get() =
        (romName.getNumBytesPlusStopByte() +
          // Game ID.
          V086Utils.Bytes.INTEGER +
          clientType.getNumBytesPlusStopByte() +
          username.getNumBytesPlusStopByte() +
          playerCountOutOfMax.getNumBytesPlusStopByte() +
          // Status.
          V086Utils.Bytes.SINGLE_BYTE)

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, romName)
      buffer.putInt(gameId)
      EmuUtil.writeString(buffer, clientType)
      EmuUtil.writeString(buffer, username)
      EmuUtil.writeString(buffer, playerCountOutOfMax)
      buffer.put(status.byteValue)
    }
  }

  companion object {
    const val ID: Byte = 0x04
  }

  object ServerStatusSerializer : MessageSerializer<ServerStatus> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<ServerStatus> {
      if (buffer.remaining() < 9) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        throw MessageFormatException("Invalid Server Status format: byte 0 = " + b.toHexString())
      }
      val numUsers = buffer.int
      val numGames = buffer.int
      val minLen = numUsers * 10 + numGames * 13
      if (buffer.remaining() < minLen) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val users: List<User> =
        (0 until numUsers).map {
          if (buffer.remaining() < 9) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val userName = EmuUtil.readString(buffer)
          if (buffer.remaining() < 8) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val ping: Long = buffer.getUnsignedInt()
          val status: Byte = buffer.get()
          val userID: Int = buffer.getUnsignedShort()
          val connectionType: Byte = buffer.get()
          User(
            userName,
            ping,
            UserStatus.fromByteValue(status),
            userID,
            ConnectionType.fromByteValue(connectionType)
          )
        }
      val games: List<Game> =
        (0 until numGames).map {
          if (buffer.remaining() < 13) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val romName = EmuUtil.readString(buffer)
          if (buffer.remaining() < 10) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val gameID = buffer.int
          val clientType = EmuUtil.readString(buffer)
          if (buffer.remaining() < 5) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val userName = EmuUtil.readString(buffer)
          if (buffer.remaining() < 3) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val players = EmuUtil.readString(buffer)
          if (buffer.remaining() < 1) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val status = buffer.get()
          Game(romName, gameID, clientType, userName, players, GameStatus.fromByteValue(status))
        }
      return MessageParseResult.Success(ServerStatus(messageNumber, users, games))
    }

    override fun write(buffer: ByteBuffer, message: ServerStatus) {
      buffer.put(0x00.toByte())
      buffer.putInt(message.users.size)
      buffer.putInt(message.games.size)
      message.users.forEach { it.writeTo(buffer) }
      message.games.forEach { it.writeTo(buffer) }
    }
  }
}
