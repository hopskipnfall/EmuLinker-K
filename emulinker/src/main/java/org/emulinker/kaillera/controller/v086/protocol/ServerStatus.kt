package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.UserStatus
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

data class ServerStatus
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val users: List<User>, val games: List<Game>) :
  V086Message() {

  override val messageTypeId = ID

  override val bodyBytes =
  // 0x00.
  V086Utils.Bytes.SINGLE_BYTE +
      // Number of users.
      V086Utils.Bytes.INTEGER +
      // Number of games.
      V086Utils.Bytes.INTEGER +
      users.sumOf { it.numBytes } +
      games.sumOf { it.numBytes }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putInt(users.size)
    buffer.putInt(games.size)
    users.forEach { it.writeTo(buffer) }
    games.forEach { it.writeTo(buffer) }
  }

  // TODO(nue): this User and Game class should not be here.
  data class User
  constructor(
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

  data class Game
  constructor(
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

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<ServerStatus> {
      return ServerStatusSerializer.read(buffer, messageNumber)
    }

    object ServerStatusSerializer : MessageSerializer<ServerStatus> {
      override val messageTypeId: Byte = ID

      override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<ServerStatus> {
        if (buffer.remaining() < 9) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val b = buffer.get()
        if (b.toInt() != 0x00) {
          throw MessageFormatException(
            "Invalid Server Status format: byte 0 = " + EmuUtil.byteToHex(b)
          )
        }
        val numUsers = buffer.int
        val numGames = buffer.int
        val minLen = numUsers * 10 + numGames * 13
        if (buffer.remaining() < minLen) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val users: MutableList<User> = ArrayList(numUsers)
        for (j in 0 until numUsers) {
          if (buffer.remaining() < 9) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val userName = EmuUtil.readString(buffer)
          if (buffer.remaining() < 8) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val ping = buffer.getUnsignedInt()
          val status = buffer.get()
          val userID = buffer.getUnsignedShort()
          val connectionType = buffer.get()
          users.add(
            User(
              userName,
              ping,
              UserStatus.fromByteValue(status),
              userID,
              ConnectionType.fromByteValue(connectionType)
            )
          )
        }
        val games: MutableList<Game> = ArrayList(numGames)
        for (j in 0 until numGames) {
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
          games.add(
            Game(romName, gameID, clientType, userName, players, GameStatus.fromByteValue(status))
          )
        }
        return MessageParseResult.Success(ServerStatus(messageNumber, users, games))
      }

      override fun write(buffer: ByteBuffer, message: ServerStatus) {
        TODO("Not yet implemented")
      }
    }
  }
}
