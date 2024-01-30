package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.discard
import io.ktor.utils.io.core.readShort
import io.ktor.utils.io.core.readShortLittleEndian
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.protocol.V086Message.Companion.parse
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedShort

class V086Bundle(val messages: Array<V086Message?>, numToWrite: Int = Int.MAX_VALUE) :
  ByteBufferMessage() {
  var numMessages: Int
    private set

  override var bodyBytesPlusMessageIdType = -1
    private set
    get() {
      if (field == -1) {
        for (i in 0 until numMessages) {
          if (messages[i] == null) break
          field += messages[i]!!.bodyBytesPlusMessageIdType
        }
      }
      return field
    }

  override fun toString(): String {
    val sb = StringBuilder()
    sb.append(
      "${this.javaClass.simpleName} ($numMessages messages) ($bodyBytesPlusMessageIdType bytes)"
    )
    sb.append(EmuUtil.LB)
    for (i in 0 until numMessages) {
      if (messages[i] == null) break
      sb.append("\tMessage ${i + 1}: ${messages[i]}${EmuUtil.LB}")
    }
    return sb.toString()
  }

  override fun writeTo(buffer: ByteBuffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(numMessages.toByte())
    for (i in 0 until numMessages) {
      val message = messages[i] ?: break
      message.writeTo(buffer)
    }
  }

  companion object {

    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(buffer: ByteBuffer, lastMessageID: Int = -1): V086Bundle {
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      if (buffer.limit() < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + buffer.limit())
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      var messageCount = buffer.get().toInt()
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (buffer.limit() < 1 + messageCount * 6) {
        throw V086BundleFormatException("Invalid bundle length: " + buffer.limit())
      }
      var parsedCount = 0
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
      // position 1 [2, 1, 0, 18, 0, 6, 0, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 0, 0, 36, 0, 3, -22, 75, 0, 80, 114, 111, 106, 101, 99, 116, 32, 54, 52, 107, 32, 48, 46, 49, 51, 32, 40, 48, 49, 32, 65, 117, 103, 32, 50, 48, 48, 51, 41, 0, 1]
      val msgNum = buffer.getChar(1).code
      //      if (1 + 1 == 2) throw ParseException("The answer is $msgNum, message length =
      // $messageCount")
      if (
        msgNum - 1 == lastMessageID || msgNum == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        messageCount = 1
        messages = arrayOfNulls(messageCount)
        val messageNumber = buffer.short.toInt() and 0xffff
        val messageLength = buffer.getShort()
        if (messageLength !in 2..buffer.remaining()) {
          throw ParseException("Invalid message length: $messageLength")
        }
        messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buffer)
        parsedCount++
      } else {
        messages = arrayOfNulls(messageCount)
        parsedCount = 0
        while (parsedCount < messageCount) {
          val messageNumber = buffer.getUnsignedShort()
          if (messageNumber <= lastMessageID) {
            if (messageNumber < 0x20 && lastMessageID > 0xFFDF) {
              // exception when messageNumber with lower value is greater do nothing
            } else {
              break
            }
          } else if (messageNumber > 0xFFBF && lastMessageID < 0x40) {
            // exception when disorder messageNumber greater that lastMessageID
            break
          }
          val messageLength = buffer.short
          if (messageLength < 2 || messageLength > buffer.remaining()) {
            throw ParseException("Invalid message length: $messageLength")
          }
          messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buffer)
          parsedCount++
        }
      }
      return V086Bundle(messages, parsedCount)
    }

    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(packet: ByteReadPacket, lastMessageID: Int = -1): V086Bundle {
//REMOVED      packet.order(ByteOrder.LITTLE_ENDIAN)
      if (packet.remaining < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + packet.remaining)
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      var messageCount = packet.readByte().toInt() //2
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (packet.remaining < 1 + messageCount * 6) {
        throw V086BundleFormatException("Invalid bundle length: " + packet.remaining)
      }
      var parsedCount = 0
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
      // headPosition: 1, [2, 1, 0, 18, 0, 6, 0, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 0, 0, 36, 0, 3, -22, 75, 0, 80, 114, 111, 106, 101, 99, 116, 32, 54, 52, 107, 32, 48, 46, 49, 51, 32, 40, 48, 49, 32, 65, 117, 103, 32, 50, 48, 48, 51, 41, 0, 1]
      val msgNum = packet.readShortLittleEndian().toInt()//1    //REMOVED .getChar(1).code
      //      if (1 + 1 == 2) throw ParseException("The answer is $msgNum, message length =
      // $messageCount")
      if (
        msgNum - 1 == lastMessageID || msgNum == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        messageCount = 1
        messages = arrayOfNulls(messageCount)
        val messageNumber = packet.readShortLittleEndian().toInt() //1
        val messageLength = packet.readShortLittleEndian() //18
        if (messageLength !in 2..packet.remaining) {
          throw ParseException("Invalid message length: $messageLength")
        }
        messages[parsedCount] = parse(messageNumber, messageLength.toInt(), packet)
        parsedCount++
      } else {
        messages = arrayOfNulls(messageCount)
        parsedCount = 0
        while (parsedCount < messageCount) {
          val messageNumber = packet.readUnsignedShort()
          if (messageNumber <= lastMessageID) {
            if (messageNumber < 0x20 && lastMessageID > 0xFFDF) {
              // exception when messageNumber with lower value is greater do nothing
            } else {
              break
            }
          } else if (messageNumber > 0xFFBF && lastMessageID < 0x40) {
            // exception when disorder messageNumber greater that lastMessageID
            break
          }
          val messageLength = packet.readShort()
          if (messageLength < 2 || messageLength > packet.remaining) {
            throw ParseException("Invalid message length: $messageLength")
          }
          messages[parsedCount] = parse(messageNumber, messageLength.toInt(), packet)
          parsedCount++
        }
      }
      return V086Bundle(messages, parsedCount)
    }
  }

  init {
    numMessages = messages.size
    if (numToWrite < numMessages) {
      numMessages = numToWrite
    }
  }
}


/*
buffer.order(ByteOrder.LITTLE_ENDIAN)
var messageCount = buffer.get().toInt() // messageCount is 2
val msgNum: Int = buffer.getChar(/* index= */ 1).code // msgNum is 1
val messageNumber = buffer.short.toInt() and 0xffff // messageNumber is 1
val messageLength = buffer.getShort() // messageLength is 18
*/

/*
var messageCount = packet.readByte().toInt() // messageCount is 2
val msgNum = packet.readShortLittleEndian().toInt() // msgNum is 1
val messageNumber = packet.readShort().toInt() and 0xffff // messageNumber is 4608
val messageLength = packet.readShortLittleEndian() // messageLength is 6
 */
