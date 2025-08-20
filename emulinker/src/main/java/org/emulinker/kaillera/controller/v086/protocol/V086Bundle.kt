package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.protocol.V086Message.Companion.parse
import org.emulinker.util.CircularVariableSizeByteArrayBuffer
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedShort

sealed interface V086Bundle : ByteBufferMessage {

  @JvmInline
  value class Single(val message: V086Message) : V086Bundle {
    override val bodyBytesPlusMessageIdType
      get() = message.bodyBytesPlusMessageIdType - 1

    override fun toString(): String {
      val sb = StringBuilder()
      sb.append("${this.javaClass.simpleName} (1 message) ($bodyBytesPlusMessageIdType bytes)")
      sb.append(EmuUtil.LB)
      sb.append("\tMessage 1: ${message}${EmuUtil.LB}")
      return sb.toString()
    }

    override fun writeTo(buffer: ByteBuf) {
      buffer.writeByte(1)
      message.writeTo(buffer)
    }

    override fun writeTo(buffer: ByteBuffer) {
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      buffer.put(1.toByte())
      message.writeTo(buffer)
    }
  }

  class Multi(val messages: Array<V086Message?>, numToWrite: Int = Int.MAX_VALUE) : V086Bundle {
    val numMessages: Int = messages.size.coerceAtMost(numToWrite)

    override val bodyBytesPlusMessageIdType
      get() = messages.sumOf { it?.bodyBytesPlusMessageIdType ?: 0 } - 1

    override fun toString(): String {
      val sb = StringBuilder()
      sb.append(
        "${this.javaClass.simpleName} ($numMessages messages) ($bodyBytesPlusMessageIdType bytes)"
      )
      sb.append(EmuUtil.LB)
      for (i in 0 until numMessages) {
        val m = messages[i] ?: break
        sb.append("\tMessage ${i + 1}: ${m}${EmuUtil.LB}")
      }
      return sb.toString()
    }

    override fun writeTo(buffer: ByteBuf) {
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
  }

  companion object {
    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(
      buffer: ByteBuffer,
      lastMessageID: Int = -1,
      arrayBuffer: CircularVariableSizeByteArrayBuffer? = null,
    ): V086Bundle {
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      if (buffer.limit() < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + buffer.limit())
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      val messageCount = buffer.get().toInt()
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (buffer.limit() < 1 + messageCount * 6) {
        throw V086BundleFormatException(
          "Invalid bundle length: ${buffer.limit()} pos = ${buffer.position()} messageCount=$messageCount"
        )
      }
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
      val msgNum = buffer.getChar(1).code
      if (
        msgNum - 1 == lastMessageID || msgNum == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        val messageNumber = buffer.short.toInt() and 0xffff
        val messageLength = buffer.getShort()
        if (messageLength !in 2..buffer.remaining()) {
          throw ParseException("Invalid message length: $messageLength")
        }
        return Single(parse(messageNumber, messageLength.toInt(), buffer, arrayBuffer))
      } else {
        val messages: Array<V086Message?> = arrayOfNulls(messageCount)
        var parsedCount = 0
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
          messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buffer, arrayBuffer)
          parsedCount++
        }
        return Multi(messages, parsedCount)
      }
    }

    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(
      buffer: ByteBuf,
      lastMessageID: Int = -1,
      arrayBuffer: CircularVariableSizeByteArrayBuffer? = null,
    ): V086Bundle {
      if (buffer.readableBytes() < 5) {
        throw V086BundleFormatException("Invalid buffer length: " + buffer.readableBytes())
      }

      // again no real need for unsigned
      // int messageCount = UnsignedUtil.getUnsignedByte(buffer);
      val messageCount = buffer.readByte().toInt()
      if (messageCount <= 0 || messageCount > 32) {
        throw V086BundleFormatException("Invalid message count: $messageCount")
      }
      if (buffer.readableBytes() < messageCount * 6) {
        throw V086BundleFormatException(
          "Invalid bundle length: ${buffer.readableBytes()} pos = ${buffer.readerIndex()} messageCount=$messageCount"
        )
      }
      val messages: Array<V086Message?>
      // buffer.getShort(1); - mistake. max value of short is 0x7FFF but we need 0xFFFF
      val msgNum = buffer.getUnsignedShortLE(1)
      if (
        msgNum - 1 == lastMessageID || msgNum == 0 && lastMessageID == 0xFFFF
      ) { // exception for 0 and 0xFFFF
        val messageNumber = buffer.readUnsignedShortLE()
        val messageLength = buffer.readShortLE()
        if (messageLength !in 2..buffer.readableBytes()) {
          throw ParseException("Invalid message length: $messageLength")
        }
        return Single(parse(messageNumber, messageLength.toInt(), buffer, arrayBuffer))
      } else {
        messages = arrayOfNulls(messageCount)
        var parsedCount = 0
        while (parsedCount < messageCount) {
          val messageNumber = buffer.readUnsignedShortLE()
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
          val messageLength = buffer.readShortLE()
          if (messageLength < 2 || messageLength > buffer.readableBytes()) {
            throw ParseException("Invalid message length: $messageLength")
          }
          messages[parsedCount] = parse(messageNumber, messageLength.toInt(), buffer, arrayBuffer)
          parsedCount++
        }
        return Multi(messages, parsedCount)
      }
    }
  }
}
