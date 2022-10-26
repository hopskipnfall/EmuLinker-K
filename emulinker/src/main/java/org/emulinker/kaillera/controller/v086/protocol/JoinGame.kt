package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
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

  override val bodyLength: Int
    get() = username.getNumBytes() + 13

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedShort(gameId)
    buffer.putUnsignedShort(val1)
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
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

    override val messageId = ID

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

    override val messageId = ID

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
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
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
  }
}
