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

data class PlayerInformation
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, val players: List<Player>) : V086Message() {
  override val messageTypeId = ID

  val numPlayers: Int
    get() = players.size

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE + V086Utils.Bytes.INTEGER + players.sumOf { it.numBytes }

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putInt(players.size)
    players.forEach { it.writeTo(buffer) }
  }

  data class Player
  constructor(
    val username: String,
    val ping: Long,
    val userId: Int,
    val connectionType: ConnectionType
  ) {
    val numBytes: Int =
      username.getNumBytesPlusStopByte() +
        V086Utils.Bytes.INTEGER +
        V086Utils.Bytes.SHORT +
        V086Utils.Bytes.SINGLE_BYTE

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username)
      buffer.putUnsignedInt(ping)
      buffer.putUnsignedShort(userId)
      buffer.put(connectionType.byteValue)
    }

    init {
      if (ping !in 0..2048) { // what should max ping be?
        throw MessageFormatException(
          "Invalid Player Information format: ping out of acceptable range: $ping"
        )
      }
      if (userId !in 0..65535) {
        throw MessageFormatException(
          "Invalid Player Information format: userID out of acceptable range: $userId"
        )
      }
    }
  }

  companion object {
    const val ID: Byte = 0x0D

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<PlayerInformation> {
      if (buffer.remaining() < 14) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        throw MessageFormatException(
          "Invalid Player Information format: byte 0 = ${EmuUtil.byteToHex(b)}"
        )
      }
      val numPlayers = buffer.int
      val minLen = numPlayers * 9
      if (buffer.remaining() < minLen) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val players: MutableList<Player> = ArrayList(numPlayers)
      for (j in 0 until numPlayers) {
        if (buffer.remaining() < 9) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val userName = EmuUtil.readString(buffer)
        if (buffer.remaining() < 7) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val ping = buffer.getUnsignedInt()
        val userID = buffer.getUnsignedShort()
        val connectionType = buffer.get()
        players.add(Player(userName, ping, userID, ConnectionType.fromByteValue(connectionType)))
      }
      return MessageParseResult.Success(PlayerInformation(messageNumber, players))
    }
  }
}
