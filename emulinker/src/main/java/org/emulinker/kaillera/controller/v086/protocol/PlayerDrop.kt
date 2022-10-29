package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.util.EmuUtil

sealed class PlayerDrop : V086Message() {
  override val messageTypeId = ID

  override val bodyBytes: Int
    get() =
      when (this) {
        is Request -> REQUEST_USERNAME
        is Notification -> username
      }.getNumBytesPlusStopByte() + V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    PlayerDropSerializer.write(buffer, this)
  }

  data class Notification
  constructor(
    override val messageNumber: Int,
    val username: String,
    /** The port number, not the player ID. */
    val playerNumber: Byte
  ) : PlayerDrop() {

    init {
      require(playerNumber in 0..255) { "playerNumber out of acceptable range: $playerNumber" }
      require(username.isNotBlank()) { "Username cannot be blank" }
    }
  }

  data class Request constructor(override val messageNumber: Int) : PlayerDrop()

  companion object {
    const val ID: Byte = 0x14

    private const val REQUEST_USERNAME = ""
    private const val REQUEST_PLAYER_NUMBER = 0.toByte()

    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<PlayerDrop> {
      return PlayerDropSerializer.read(buffer, messageNumber)
    }
  }

  object PlayerDropSerializer : MessageSerializer<PlayerDrop> {
    override val messageTypeId: Byte = ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<PlayerDrop> {
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      val playerNumber = buffer.get()
      return MessageParseResult.Success(
        if (userName == REQUEST_USERNAME && playerNumber == REQUEST_PLAYER_NUMBER) {
          Request(messageNumber)
        } else Notification(messageNumber, userName, playerNumber)
      )
    }

    override fun write(buffer: ByteBuffer, message: PlayerDrop) {
      EmuUtil.writeString(
        buffer,
        when (message) {
          is Request -> REQUEST_USERNAME
          is Notification -> message.username
        }
      )
      buffer.put(
        when (message) {
          is Request -> REQUEST_PLAYER_NUMBER
          is Notification -> message.playerNumber
        }
      )
    }
  }
}
