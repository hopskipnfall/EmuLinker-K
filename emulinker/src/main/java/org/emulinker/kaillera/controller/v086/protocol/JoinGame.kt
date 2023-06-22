package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class JoinGame : V086Message() {
  override val messageTypeId = ID

  abstract val gameId: Int
  abstract val connectionType: ConnectionType

  override val bodyBytes: Int
    get() =
      V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SHORT +
        when (this) {
          is JoinGameRequest -> REQUEST_USERNAME
          is JoinGameNotification -> this.username
        }.getNumBytesPlusStopByte() +
        V086Utils.Bytes.INTEGER +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    JoinGameSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x0C

    private const val REQUEST_VAL1 = 0
    private const val REQUEST_USERNAME = ""
    private const val REQUEST_PING = 0L
    private const val REQUEST_USER_ID = 0xFFFF
  }

  object JoinGameSerializer : MessageSerializer<JoinGame> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<JoinGame> {
      if (buffer.remaining() < 13) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00)
        throw MessageFormatException("Invalid format: byte 0 = " + EmuUtil.byteToHex(b))
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 7) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val ping = buffer.getUnsignedInt()
      val userID = buffer.getUnsignedShort()
      val connectionType = buffer.get()
      return MessageParseResult.Success(
        if (userName.isBlank() && ping == 0L && userID == 0xFFFF)
          JoinGameRequest(messageNumber, gameID, ConnectionType.fromByteValue(connectionType))
        else
          JoinGameNotification(
            messageNumber,
            gameID,
            val1,
            userName,
            ping,
            userID,
            ConnectionType.fromByteValue(connectionType)
          )
      )
    }

    override fun write(buffer: ByteBuffer, message: JoinGame) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(message.gameId)
      buffer.putUnsignedShort(
        when (message) {
          is JoinGameRequest -> REQUEST_VAL1
          is JoinGameNotification -> message.val1
        }
      )
      EmuUtil.writeString(
        buffer,
        when (message) {
          is JoinGameRequest -> REQUEST_USERNAME
          is JoinGameNotification -> message.username
        }
      )
      buffer.putUnsignedInt(
        when (message) {
          is JoinGameRequest -> REQUEST_PING
          is JoinGameNotification -> message.ping
        }
      )
      buffer.putUnsignedShort(
        when (message) {
          is JoinGameRequest -> REQUEST_USER_ID
          is JoinGameNotification -> message.userId
        }
      )
      buffer.put(message.connectionType.byteValue)
    }
  }
}

/**
 * Server message indiciating a user successfully joined a game.
 *
 * This shares a message type ID with [JoinGameRequest]: `0x0C`.
 */
data class JoinGameNotification(
  override val messageNumber: Int,
  override val gameId: Int,
  val val1: Int,
  val username: String,
  val ping: Long,
  val userId: Int,
  override val connectionType: ConnectionType
) : JoinGame(), ServerMessage {

  init {
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    require(ping in 0..0xFFFF) { "ping out of acceptable range: $ping" }
    require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
    require(username.isNotBlank()) { "Username cannot be empty" }
  }
}

/**
 * Client message from a user requesting to join a game.
 *
 * This shares a message type ID with [JoinGameNotification]: `0x0C`.
 */
data class JoinGameRequest(
  override val messageNumber: Int,
  override val gameId: Int,
  override val connectionType: ConnectionType
) : JoinGame(), ClientMessage {

  init {
    require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
  }
}
