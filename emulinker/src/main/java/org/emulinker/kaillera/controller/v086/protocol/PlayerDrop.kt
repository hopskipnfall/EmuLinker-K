package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
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
  override val bodyBytes: Int
    get() = username.getNumBytesPlusStopByte() + V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    buffer.put(playerNumber)
  }

  data class Notification
  @Throws(MessageFormatException::class)
  constructor(
    override val messageNumber: Int,
    override val username: String,
    // TODO(nue): Should we really be using a byte for this??
    override val playerNumber: Byte
  ) : PlayerDrop() {

    override val messageTypeId = ID

    init {
      require(playerNumber in 0..255) { "playerNumber out of acceptable range: $playerNumber" }
      require(username.isNotBlank()) { "Username cannot be blank" }
    }
  }

  data class Request
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : PlayerDrop() {

    override val messageTypeId = ID

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
      val userName = EmuUtil.readString(buffer)
      val playerNumber = buffer.get()
      return MessageParseResult.Success(
        if (userName.isBlank() && playerNumber.toInt() == 0) {
          Request(messageNumber)
        } else Notification(messageNumber, userName, playerNumber)
      )
    }

    object PlayerDropRequestSerializer : MessageSerializer<PlayerDrop.Request> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<PlayerDrop.Request> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: PlayerDrop.Request) {
        TODO("Not yet implemented")
      }
    }

    object PlayerDropNotificationSerializer : MessageSerializer<PlayerDrop.Notification> {
      override val messageTypeId: Byte = TODO("Not yet implemented")

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<PlayerDrop.Notification> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: PlayerDrop.Notification) {
        TODO("Not yet implemented")
      }
    }
  }
}
