package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedInt

sealed class Ack : V086Message() {
  override val bodyBytes =
    V086Utils.Bytes.SINGLE_BYTE +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER +
      V086Utils.Bytes.INTEGER

  object ClientAckSerializer : MessageSerializer<ClientAck> {
    override val messageTypeId: Byte = ClientAck.ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<ClientAck> {
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

    override fun write(buffer: ByteBuffer, message: ClientAck) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedInt(0L)
      buffer.putUnsignedInt(1L)
      buffer.putUnsignedInt(2L)
      buffer.putUnsignedInt(3L)
    }
  }

  object ServerAckSerializer : MessageSerializer<ServerAck> {
    override val messageTypeId: Byte = ServerAck.ID

    override fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<ServerAck> {
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

    override fun write(buffer: ByteBuffer, message: ServerAck) {
      buffer.put(0x00.toByte())
      buffer.putUnsignedInt(0L)
      buffer.putUnsignedInt(1L)
      buffer.putUnsignedInt(2L)
      buffer.putUnsignedInt(3L)
    }
  }
}

/**
 * This is effectively a ping response sent by the client when it receives a [ServerAck].
 *
 * This back and forth is used to establish a client's "ping" (millisecond delay) at login and to
 * confirm that the client is still listening on the port.
 *
 * Message type ID: `0x06`.
 */
data class ClientAck(override val messageNumber: Int) : Ack(), ClientMessage {
  override val messageTypeId = ID

  public override fun writeBodyTo(buffer: ByteBuffer) {
    ClientAckSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x06
  }
}

/**
 * A message the server sends to the client, from which it expects a [ClientAck] in response.
 *
 * This back and forth is used to establish a client's "ping" (millisecond delay) at login and to
 * confirm that the client is still listening on the port.
 *
 * Message type ID: `0x05`.
 */
data class ServerAck(override val messageNumber: Int) : Ack(), ServerMessage {
  override val messageTypeId = ID

  public override fun writeBodyTo(buffer: ByteBuffer) {
    ServerAckSerializer.write(buffer, this)
  }

  companion object {
    const val ID: Byte = 0x05
  }
}
