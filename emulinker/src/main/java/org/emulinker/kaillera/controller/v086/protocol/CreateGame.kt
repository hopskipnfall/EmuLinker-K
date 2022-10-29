package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class CreateGame : V086Message() {
  abstract val username: String
  abstract val romName: String
  abstract val clientType: String
  abstract val gameId: Int
  abstract val val1: Int
  override val bodyBytes: Int
    get() =
      username.getNumBytesPlusStopByte() +
        romName.getNumBytesPlusStopByte() +
        clientType.getNumBytesPlusStopByte() +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SHORT

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    EmuUtil.writeString(buffer, romName)
    EmuUtil.writeString(buffer, clientType)
    buffer.putUnsignedShort(gameId)
    buffer.putUnsignedShort(val1)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val romName: String,
    override val clientType: String,
    override val gameId: Int,
    override val val1: Int
  ) : CreateGame() {

    override val messageTypeId = ID

    init {
      require(romName.isNotBlank()) { "romName cannot be blank" }
      require(gameId in 0..0xFFFF) { "gameID out of acceptable range: $gameId" }
      require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int, override val romName: String) : CreateGame() {

    override val messageTypeId = ID

    override val username = ""
    override val clientType = ""
    override val gameId = 0xFFFF
    override val val1 = 0xFFFF
  }

  companion object {
    const val ID: Byte = 0x0A

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<CreateGame> {
      if (buffer.remaining() < 8) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 6) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val romName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val clientType = EmuUtil.readString(buffer)
      if (buffer.remaining() < 4) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val gameID = buffer.getUnsignedShort()
      val val1 = buffer.getUnsignedShort()
      return MessageParseResult.Success(
        if (userName.isBlank() && gameID == 0xFFFF && val1 == 0xFFFF)
          Request(messageNumber, romName)
        else Notification(messageNumber, userName, romName, clientType, gameID, val1)
      )
    }

    object CreateGameNotificationSerializer : MessageSerializer<CreateGame.Notification> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<CreateGame.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: CreateGame.Notification) {
        TODO("Not yet implemented")
      }
    }

    object CreateGameRequestSerializer : MessageSerializer<CreateGame.Request> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<CreateGame.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: CreateGame.Request) {
        TODO("Not yet implemented")
      }
    }
  }
}
