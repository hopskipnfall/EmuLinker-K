package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytes
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil

sealed class PlayerDrop : V086Message() {
  abstract val username: String
  abstract val playerNumber: Byte

  // public PlayerDrop(int messageNumber, String userName, byte playerNumber)
  //     throws MessageFormatException {
  //   super(messageNumber);
  //   if (playerNumber < 0 || playerNumber > 255)
  //     throw new MessageFormatException(
  //         "Invalid "
  //             + getDescription()
  //             + " format: playerNumber out of acceptable range: "
  //             + playerNumber);
  //   this.userName = userName;
  //   this.playerNumber = playerNumber;
  // }
  override val bodyLength: Int
    get() = username.getNumBytes() + 2

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
    buffer.put(playerNumber)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    override val playerNumber: Byte
  ) : PlayerDrop() {

    override val messageId = ID

    init {
      require(playerNumber in 0..255) { "playerNumber out of acceptable range: $playerNumber" }
      require(username.isNotBlank()) { "Username cannot be blank" }
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : PlayerDrop() {

    override val messageId = ID

    override val username = ""
    override val playerNumber = 0.toByte()
  }

  companion object {
    const val ID: Byte = 0x14
    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<PlayerDrop> {
      if (buffer.remaining() < 2) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
      val playerNumber = buffer.get()
      return MessageParseResult.Success(
        if (userName.isBlank() && playerNumber.toInt() == 0) {
          Request(messageNumber)
        } else Notification(messageNumber, userName, playerNumber)
      )
    }
  }
}
