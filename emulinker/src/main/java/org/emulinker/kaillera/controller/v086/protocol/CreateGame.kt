package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.readString
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedShort

sealed class CreateGame : V086Message() {
  abstract val romName: String

  companion object {
    const val ID: Byte = 0x0A

    const val REQUEST_GAME_ID = 0xFFFF
    const val REQUEST_VAL1 = 0xFFFF
    const val REQUEST_USERNAME = ""
    const val REQUEST_CLIENT_TYPE = ""
  }

  object CreateGameSerializer : MessageSerializer<CreateGame> {
    override val messageTypeId: Byte = ID
    override fun read(packet: ByteReadPacket, messageNumber: Int): Result<CreateGame> {
      if (packet.remaining < 8) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = packet.readString()
      if (packet.remaining < 6) {
        return parseFailure("Failed byte count validation!")
      }
      val romName = packet.readString()
      if (packet.remaining < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val clientType = packet.readString()
      if (packet.remaining < 4) {
        return parseFailure("Failed byte count validation!")
      }
      val gameID = packet.readUnsignedShort()
      val val1 = packet.readUnsignedShort()
      return Result.success(
        if (userName == REQUEST_USERNAME && gameID == REQUEST_GAME_ID && val1 == REQUEST_VAL1)
          CreateGameRequest(messageNumber, romName)
        else CreateGameNotification(messageNumber, userName, romName, clientType, gameID, val1)
      )
    }

    override fun read(buffer: ByteBuffer, messageNumber: Int): Result<CreateGame> {
      if (buffer.remaining() < 8) {
        return parseFailure("Failed byte count validation!")
      }
      val userName = buffer.readString()
      if (buffer.remaining() < 6) {
        return parseFailure("Failed byte count validation!")
      }
      val romName = buffer.readString()
      if (buffer.remaining() < 5) {
        return parseFailure("Failed byte count validation!")
      }
      val clientType = buffer.readString()
      if (buffer.remaining() < 4) {
        return parseFailure("Failed byte count validation!")
      }
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      return Result.success(
        if (userName == REQUEST_USERNAME && gameID == REQUEST_GAME_ID && val1 == REQUEST_VAL1)
          CreateGameRequest(messageNumber, romName)
        else CreateGameNotification(messageNumber, userName, romName, clientType, gameID, val1)
      )
    }

    override fun write(buffer: ByteBuffer, message: CreateGame) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is CreateGameRequest -> REQUEST_USERNAME
          is CreateGameNotification -> message.username
        }
      )
      EmuUtil.writeString(buffer, message.romName)
      EmuUtil.writeString(
        buffer,
        when (message) {
          is CreateGameRequest -> REQUEST_CLIENT_TYPE
          is CreateGameNotification -> message.clientType
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is CreateGameRequest -> REQUEST_GAME_ID
          is CreateGameNotification -> message.gameId
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is CreateGameRequest -> REQUEST_VAL1
          is CreateGameNotification -> message.val1
        }
      )
    }
  }
}

/**
 * Server message indicating that a new game has been created.
 *
 * This message shares a message type with [CreateGameRequest]: `0x0A`.
 */
data class CreateGameNotification(
  override val messageNumber: Int,
  val username: String,
  override val romName: String,
  val clientType: String,
  val gameId: Int,
  val val1: Int
) : CreateGame(), ServerMessage {

  override val messageTypeId = ID

  init {
    require(romName.isNotBlank()) { "romName cannot be blank" }
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
  }

  override val bodyBytes: Int
    get() =
      username.getNumBytesPlusStopByte() +
        romName.getNumBytesPlusStopByte() +
        clientType.getNumBytesPlusStopByte() +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SHORT

  public override fun writeBodyTo(buffer: ByteBuffer) {
    CreateGameSerializer.write(buffer, this)
  }
}

/**
 * Client message requesting to create a new game.
 *
 * This message shares a message type with [CreateGameRequest]: `0x0A`.
 */
data class CreateGameRequest(override val messageNumber: Int, override val romName: String) :
  CreateGame(), ClientMessage {
  override val messageTypeId = ID

  private val username = ""
  private val clientType = ""

  override val bodyBytes: Int
    get() =
      username.getNumBytesPlusStopByte() +
        romName.getNumBytesPlusStopByte() +
        clientType.getNumBytesPlusStopByte() +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SHORT

  public override fun writeBodyTo(buffer: ByteBuffer) {
    CreateGameSerializer.write(buffer, this)
  }
}
