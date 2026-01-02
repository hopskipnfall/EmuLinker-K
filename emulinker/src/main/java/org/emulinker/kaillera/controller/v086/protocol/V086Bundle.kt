package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.ByteBuf
import io.netty.util.ReferenceCountUtil
import io.netty.util.ReferenceCounted
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.util.EmuUtil

sealed interface V086Bundle : ByteBufferMessage, ReferenceCounted {

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

    override fun refCnt(): Int = (message as? ReferenceCounted)?.refCnt() ?: TODO("Unsupported!")

    override fun retain(): V086Bundle {
      ReferenceCountUtil.retain(message)
      return this
    }

    override fun retain(increment: Int): V086Bundle {
      ReferenceCountUtil.retain(message, increment)
      return this
    }

    override fun touch(): V086Bundle {
      ReferenceCountUtil.touch(message)
      return this
    }

    override fun touch(hint: Any?): V086Bundle {
      ReferenceCountUtil.touch(message, hint)
      return this
    }

    override fun release(): Boolean {
      return ReferenceCountUtil.release(message)
    }

    override fun release(decrement: Int): Boolean {
      return ReferenceCountUtil.release(message, decrement)
    }
  }

  class Multi(val messages: Array<V086Message?>, numToWrite: Int = Int.MAX_VALUE) : V086Bundle {
    val numMessages: Int = messages.size.coerceAtMost(numToWrite)

    override val bodyBytesPlusMessageIdType
      get() = messages.take(numMessages).sumOf { it?.bodyBytesPlusMessageIdType ?: 0 } - 1

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

    override fun refCnt(): Int {
      TODO("Unsupported!")
    }

    override fun retain(): V086Bundle {
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          ReferenceCountUtil.retain(m)
        }
      }
      return this
    }

    override fun retain(increment: Int): V086Bundle {
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          ReferenceCountUtil.retain(m, increment)
        }
      }
      return this
    }

    override fun touch(): V086Bundle {
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          ReferenceCountUtil.touch(m)
        }
      }
      return this
    }

    override fun touch(hint: Any?): V086Bundle {
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          ReferenceCountUtil.touch(m, hint)
        }
      }
      return this
    }

    override fun release(): Boolean {
      var allReleased = true
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          if (!ReferenceCountUtil.release(m)) {
            allReleased = false
          }
        }
      }
      return allReleased
    }

    override fun release(decrement: Int): Boolean {
      var allReleased = true
      for (i in 0 until numMessages) {
        val m = messages[i]
        if (m != null) {
          if (!ReferenceCountUtil.release(m, decrement)) {
            allReleased = false
          }
        }
      }
      return allReleased
    }
  }

  companion object {

    @Throws(ParseException::class, V086BundleFormatException::class, MessageFormatException::class)
    fun parse(buffer: ByteBuf, lastMessageID: Int = -1): V086Bundle {
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
        return Single(V086Message.parse(messageNumber, messageLength.toInt(), buffer))
      } else {
        messages = arrayOfNulls(messageCount)
        var parsedCount = 0
        try {
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
            messages[parsedCount] = V086Message.parse(messageNumber, messageLength.toInt(), buffer)
            parsedCount++
          }
        } catch (t: Throwable) {
          // Leak fix: If we fail halfway, release what we successfully parsed
          for (i in 0..parsedCount) {
            val m = messages[i]
            if (m != null) {
              ReferenceCountUtil.release(m)
            }
          }
          throw t
        }
        return Multi(messages, parsedCount)
      }
    }
  }
}
