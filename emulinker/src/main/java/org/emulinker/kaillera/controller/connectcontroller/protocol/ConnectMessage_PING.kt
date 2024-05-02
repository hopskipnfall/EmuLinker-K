package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlin.Throws
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.pico.AppModule

// TODO(nue): Turn into a data class?
class ConnectMessage_PING : ConnectMessage() {
  override val iD = ID

  override fun toString() = "Client Ping"

  override val bodyBytesPlusMessageIdType = ID.length + 1

  override fun writeTo(buffer: ByteBuf) {
    buffer.writeBytes(AppModule.charsetDoNotUse.encode(ID))
    buffer.writeByte(0x00)
  }

  override fun writeTo(buffer: ByteBuffer) {
    buffer.put(AppModule.charsetDoNotUse.encode(ID))
    buffer.put(0x00.toByte())
  }

  companion object {
    const val ID = "PING"

    @Throws(MessageFormatException::class)
    fun parse(msg: String): ConnectMessage {
      if (msg.length != 5) throw MessageFormatException("Invalid message length!")
      if (!msg.startsWith(ID)) throw MessageFormatException("Invalid message identifier!")
      if (msg.last().code != 0x00) throw MessageFormatException("Invalid message stop byte!")
      return ConnectMessage_PING()
    }
  }
}
