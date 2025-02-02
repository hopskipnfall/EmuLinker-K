package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.readShortLittleEndian
import io.ktor.utils.io.core.remaining
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.io.Source
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.protocol.V086Message.Companion.parse
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.readUnsignedShortLittleEndian

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

  override fun writeTo(buffer: ByteBuf) {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.writeByte(numMessages)
    for (i in 0 until numMessages) {
      val message = messages[i] ?: break
      message.writeTo(buffer)
    }
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
        throw V086BundleFormatException(
          "Invalid bundle length: ${buffer.limit()} pos = ${buffer.position()} messageCount=$messageCount"
        )
      }
      var parsedCount = 0
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
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
    fun parse(buf: ByteBuf, lastMessageID: Int = -1): V086Bundle {
      buf.order(ByteOrder.LITTLE_ENDIAN)
      if (buf.readableBytes() < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + buf.readableBytes())
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      var messageCount = buf.readByte().toInt()
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (buf.readableBytes() < 1 + messageCount * 6) {
        // TODO(nue): Probably delete me!!
        //        // From whom? Full buffer dump?
        //        data class Hey(
        //          val fromUserId: Int,
        //          val readerPosotion: Int,
        //          val readableBytes: Int,
        //          val fullDump: String,
        //          val message: String
        //        )
        throw V086BundleFormatException("Invalid bundle length: " + buf.readableBytes())
      }
      var parsedCount = 0
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
      val msgNum = buf.getChar(1).code
      //      if (1 + 1 == 2) throw ParseException("The answer is $msgNum, message length =
      // $messageCount")
      if (
        msgNum - 1 == lastMessageID || msgNum == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        messageCount = 1
        messages = arrayOfNulls(messageCount)
        val messageNumber = buf.readShort().toInt() and 0xffff
        val messageLength = buf.readShort()
        if (messageLength !in 2..buf.readableBytes()) {
          throw ParseException("Invalid message length: $messageLength")
        }
        messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buf)
        parsedCount++
      } else {
        messages = arrayOfNulls(messageCount)
        parsedCount = 0
        while (parsedCount < messageCount) {
          val messageNumber = buf.readUnsignedShort()
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
          val messageLength = buf.readShort()
          if (messageLength < 2 || messageLength > buf.readableBytes()) {
            throw ParseException("Invalid message length: $messageLength")
          }
          messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buf)
          parsedCount++
        }
      }
      return V086Bundle(messages, parsedCount)
    }

    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(packet: Source, lastMessageID: Int = -1): V086Bundle {
      if (packet.remaining < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + packet.remaining)
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      var messageCount = packet.readByte().toInt()
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (packet.remaining < 1 + messageCount * 6) {
        throw V086BundleFormatException("Invalid bundle length: " + packet.remaining)
      }
      var parsedCount = 0
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF

      val firstMessageNumber = packet.readShortLittleEndian().toInt()
      //      if (1 + 1 == 2) throw ParseException("The answer is $msgNum, message length =
      // $messageCount")
      if (
        firstMessageNumber - 1 == lastMessageID ||
          firstMessageNumber == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        messageCount = 1
        messages = arrayOfNulls(messageCount)
        val messageLength = packet.readShortLittleEndian()
        if (messageLength !in 2..packet.remaining) {
          throw ParseException("Invalid message length: $messageLength")
        }
        messages[parsedCount] = parse(firstMessageNumber, messageLength.toInt(), packet)
        parsedCount++
      } else {
        messages = arrayOfNulls(messageCount)
        parsedCount = 0
        var usedFirstMessageNumberAlready = false
        while (parsedCount < messageCount) {
          val messageNumber =
            if (usedFirstMessageNumberAlready) {
              packet.readUnsignedShortLittleEndian()
            } else {
              usedFirstMessageNumberAlready = true
              firstMessageNumber
            }

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
          val messageLength = packet.readShortLittleEndian()
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
