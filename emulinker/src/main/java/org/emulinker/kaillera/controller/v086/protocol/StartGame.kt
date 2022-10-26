package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.util.UnsignedUtil.getUnsignedByte
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedByte
import org.emulinker.util.UnsignedUtil.putUnsignedShort

abstract class StartGame : V086Message() {
  override val messageId = ID

  abstract val val1: Int
  abstract val playerNumber: Short
  abstract val numPlayers: Short

  override val bodyLength: Int
    get() = 5

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedShort(val1)
    buffer.putUnsignedByte(playerNumber.toInt())
    buffer.putUnsignedByte(numPlayers.toInt())
  }

  companion object {
    const val ID: Byte = 0x11

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): StartGame {
      if (buffer.remaining() < 5) throw ParseException("Failed byte count validation!")
      val b = buffer.get()
      if (b.toInt() != 0x00) throw ParseException("Failed byte count validation!")
      val val1 = buffer.getUnsignedShort()
      val playerNumber = buffer.getUnsignedByte()
      val numPlayers = buffer.getUnsignedByte()
      return if (val1 == 0xFFFF && playerNumber.toInt() == 0xFF && numPlayers.toInt() == 0xFF)
        StartGame_Request(messageNumber)
      else StartGame_Notification(messageNumber, val1, playerNumber, numPlayers)
    }
  }
}
