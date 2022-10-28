package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedInt

sealed class Ack : V086Message() {
  abstract val val1: Long
  abstract val val2: Long
  abstract val val3: Long
  abstract val val4: Long

  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER

  data class ClientAck
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : Ack() {
    override val val1: Long = 0
    override val val2: Long = 1
    override val val3: Long = 2
    override val val4: Long = 3

    override val messageTypeId = ID

    public override fun writeBodyTo(buffer: ByteBuffer) {
      ClientAckSerializer.write(buffer, this)
    }

    companion object {
      const val ID: Byte = 0x06

      @Throws(ParseException::class, MessageFormatException::class)
      fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<Ack.ClientAck> {
        if (buffer.remaining() < 17) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val b = buffer.get()
        if (b.toInt() != 0x00) {
          throw MessageFormatException(
            "Invalid Client to Server ACK format: byte 0 = ${EmuUtil.byteToHex(b)}"
          )
        }
        return MessageParseResult.Success(ClientAck(messageNumber))
      }

      object ClientAckSerializer : MessageSerializer<Ack.ClientAck> {
        override fun read(
          buffer: ByteBuffer,
          messageNumber: Int
        ): MessageParseResult<Ack.ClientAck> {
          if (buffer.remaining() < 17) {
            return MessageParseResult.Failure("Failed byte count validation!")
          }
          val b = buffer.get()
          if (b.toInt() != 0x00) {
            throw MessageFormatException(
              "Invalid " + "Client to Server ACK" + " format: byte 0 = " + EmuUtil.byteToHex(b)
            )
          }

          return MessageParseResult.Success(Ack.ClientAck(messageNumber))
        }

        override fun write(buffer: ByteBuffer, message: Ack.ClientAck) {
          buffer.put(0x00.toByte())
          buffer.putUnsignedInt(message.val1)
          buffer.putUnsignedInt(message.val2)
          buffer.putUnsignedInt(message.val3)
          buffer.putUnsignedInt(message.val4)
        }
      }
    }
  }

  data class ServerAck
  @Throws(MessageFormatException::class)
  constructor(override val messageNumber: Int) : Ack() {
    override val val1 = 0L
    override val val2 = 1L
    override val val3 = 2L
    override val val4 = 3L

    override val messageTypeId = ID

    public override fun writeBodyTo(buffer: ByteBuffer) {
      ServerAckSerializer.write(buffer, this)
    }

    companion object {
      const val ID: Byte = 0x05

      @Throws(ParseException::class, MessageFormatException::class)
      fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<ServerAck> {
        if (buffer.remaining() < 17) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val b = buffer.get()
        if (b.toInt() != 0x00) {
          throw MessageFormatException("byte 0 = " + EmuUtil.byteToHex(b))
        }
        val val1 = buffer.getUnsignedInt()
        val val2 = buffer.getUnsignedInt()
        val val3 = buffer.getUnsignedInt()
        val val4 = buffer.getUnsignedInt()
        if (val1 != 0L || val2 != 1L || val3 != 2L || val4 != 3L)
          throw MessageFormatException(
            "Invalid Server to Client ACK format: bytes do not match acceptable format!"
          )
        return MessageParseResult.Success(ServerAck(messageNumber))
      }
    }

    object ServerAckSerializer : MessageSerializer<Ack.ServerAck> {
      override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<Ack.ServerAck> {
        if (buffer.remaining() < 17) {
          return MessageParseResult.Failure("Failed byte count validation!")
        }
        val b = buffer.get()
        if (b.toInt() != 0x00) {
          throw MessageFormatException(
            "Invalid " + "Client to Server ACK" + " format: byte 0 = " + EmuUtil.byteToHex(b)
          )
        }

        return MessageParseResult.Success(Ack.ServerAck(messageNumber))
      }

      override fun write(buffer: ByteBuffer, message: Ack.ServerAck) {
        buffer.put(0x00.toByte())
        buffer.putUnsignedInt(message.val1)
        buffer.putUnsignedInt(message.val2)
        buffer.putUnsignedInt(message.val3)
        buffer.putUnsignedInt(message.val4)
      }
    }
  }
}
