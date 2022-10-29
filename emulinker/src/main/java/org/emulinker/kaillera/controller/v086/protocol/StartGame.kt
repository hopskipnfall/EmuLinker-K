package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class StartGame : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE +
        V086Utils.Bytes.SINGLE_BYTE

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    val val1: Int,
    val playerNumber: Short,
    val numPlayers: Short
  ) : StartGame() {

    override val messageTypeId = ID

    init {
      require(val1 in 0..0xFFFF) { "val1 out of acceptable range: $val1" }
      require(playerNumber in 0..0xFF) { "playerNumber out of acceptable range: $playerNumber" }
      require(numPlayers in 0..0xFF) { "numPlayers out of acceptable range: $numPlayers" }
    }

    public override fun writeBodyTo(buffer: ByteBuffer) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(val1)
      buffer.putUnsignedByte(playerNumber.toInt())
      buffer.putUnsignedByte(numPlayers.toInt())
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : StartGame() {
    private val val1 = 0xFFFF
    private val playerNumber = 0xFF.toShort()
    private val numPlayers = 0xFF.toShort()

    public override fun writeBodyTo(buffer: ByteBuffer) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedShort(val1)
      buffer.putUnsignedByte(playerNumber.toInt())
      buffer.putUnsignedByte(numPlayers.toInt())
    }
  }

  companion object {
    const val ID: Byte = 0x11

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<StartGame> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val val1 = buffer.getUnsignedShort()
      val playerNumber = buffer.getUnsignedByte()
      val numPlayers = buffer.getUnsignedByte()
      return MessageParseResult.Success(
        if (val1 == 0xFFFF && playerNumber.toInt() == 0xFF && numPlayers.toInt() == 0xFF)
          Request(messageNumber)
        else Notification(messageNumber, val1, playerNumber, numPlayers)
      )
    }

    object StartGameRequestSerializer : MessageSerializer<StartGame.Request> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<StartGame.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: StartGame.Request) {
        TODO("Not yet implemented")
      }
    }

    object StartGameNotificationSerializer : MessageSerializer<StartGame.Notification> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<StartGame.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: StartGame.Notification) {
        TODO("Not yet implemented")
      }
    }
  }
}
