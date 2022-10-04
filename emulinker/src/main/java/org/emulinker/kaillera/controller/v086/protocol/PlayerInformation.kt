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

data class PlayerInformation
    @Throws(MessageFormatException::class)
    constructor(override val messageNumber: Int, val players: List<Player>) : V086Message() {
  override val messageId = ID

  init {
    validateMessageNumber(messageNumber)
  }

  val numPlayers: Int
    get() = players.size

  override val bodyLength: Int
    get() = 5 + players.sumOf { it.numBytes }

  override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putInt(players.size)
    players.forEach { it.writeTo(buffer) }
  }

  data class Player
      constructor(
          val username: String, val ping: Long, val userId: Int, val connectionType: ConnectionType
      ) {

    val numBytes: Int = username.getNumBytes() + 8

    fun writeTo(buffer: ByteBuffer) {
      EmuUtil.writeString(buffer, username, 0x00, AppModule.charsetDoNotUse)
      buffer.putUnsignedInt(ping)
      buffer.putUnsignedShort(userId)
      buffer.put(connectionType.byteValue)
    }

    init {
      if (ping !in 0..2048) { // what should max ping be?
        throw MessageFormatException(
            "Invalid Player Information format: ping out of acceptable range: $ping")
      }
      if (userId !in 0..65535) {
        throw MessageFormatException(
            "Invalid Player Information format: userID out of acceptable range: $userId")
      }
    }
  }

  companion object {
    const val ID: Byte = 0x0D

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): PlayerInformation {
      if (buffer.remaining() < 14) throw ParseException("Failed byte count validation!")
      val b = buffer.get()
      if (b.toInt() != 0x00) {
        throw MessageFormatException(
            "Invalid Player Information format: byte 0 = ${EmuUtil.byteToHex(b)}")
      }
      val numPlayers = buffer.int
      val minLen = numPlayers * 9
      if (buffer.remaining() < minLen) throw ParseException("Failed byte count validation!")
      val players: MutableList<Player> = ArrayList(numPlayers)
      for (j in 0 until numPlayers) {
        if (buffer.remaining() < 9) {
          throw ParseException("Failed byte count validation!")
        }
        val userName = EmuUtil.readString(buffer, 0x00, AppModule.charsetDoNotUse)
        if (buffer.remaining() < 7) {
          throw ParseException("Failed byte count validation!")
        }
        val ping = buffer.getUnsignedInt()
        val userID = buffer.getUnsignedShort()
        val connectionType = buffer.get()
        players.add(Player(userName, ping, userID, ConnectionType.fromByteValue(connectionType)))
      }
      return PlayerInformation(messageNumber, players)
    }
  }
}
