package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class JoinGame : V086Message() {
  abstract val gameId: Int
  abstract val val1: Int
  abstract val username: String
  abstract val ping: Long
  abstract val userId: Int
  abstract val connectionType: ConnectionType

  override val bodyBytes: Int
    get() =
      V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SHORT +
        username.getNumBytesPlusStopByte() +
        V086Utils.Bytes.INTEGER +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedShort(gameId)
    buffer.putUnsignedShort(val1)
    EmuUtil.writeString(buffer, username)
    buffer.putUnsignedInt(ping)
    buffer.putUnsignedShort(userId)
    buffer.put(connectionType.byteValue)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val gameId: Int,
    override val val1: Int,
    override val username: String,
    override val ping: Long,
    override val userId: Int,
    override val connectionType: ConnectionType
  ) : JoinGame() {

    override val messageTypeId = ID

    init {
      require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
      require(ping in 0..0xFFFF) { "ping out of acceptable range: $ping" }
      require(userId in 0..0xFFFF) { "UserID out of acceptable range: $userId" }
      require(username.isNotBlank()) { "Username cannot be empty" }
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val gameId: Int,
    override val connectionType: ConnectionType
  ) : JoinGame() {

    override val messageTypeId = ID

    override val val1 = 0
    override val username = ""
    override val ping = 0L
    override val userId = 0xFFFF

    init {
      require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
    }
  }

  companion object {
    const val ID: Byte = 0x0C

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<JoinGame> {
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
          Request(messageNumber, gameID, ConnectionType.fromByteValue(connectionType))
        else
          Notification(
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

    object JoinGameRequestSerializer : MessageSerializer<JoinGame.Request> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<JoinGame.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: JoinGame.Request) {
        TODO("Not yet implemented")
      }
    }

    object JoinGameNotificationSerializer : MessageSerializer<JoinGame.Notification> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<JoinGame.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: JoinGame.Notification) {
        TODO("Not yet implemented")
      }
    }
  }
}
