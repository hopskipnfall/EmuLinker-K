package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlin.Throws
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.EmuUtil

/**
 * Message client sends to request a private server port.
 *
 * See [ConnectMessage] for more documentation on the handshake. This message was formerly called
 * `ConnectMessage_HELLO`.
 */
data class RequestPrivateKailleraPortRequest(val protocol: String) : ConnectMessage() {
  override val iD = ID

  override val bodyBytesPlusMessageIdType = ID.length + protocol.length + 1

  override fun writeTo(buffer: ByteBuf) {
    buffer.writeBytes(AppModule.charsetDoNotUse.encode(iD))
    EmuUtil.writeString(buffer, protocol, 0x00, AppModule.charsetDoNotUse)
  }

  override fun writeTo(buffer: ByteBuffer) {
    buffer.put(AppModule.charsetDoNotUse.encode(iD))
    EmuUtil.writeString(buffer, protocol, 0x00, AppModule.charsetDoNotUse)
  }

  companion object {
    const val ID = "HELLO"

    @Throws(MessageFormatException::class)
    fun parse(msg: String): ConnectMessage {
      if (msg.length < ID.length + 2) throw MessageFormatException("Invalid message length!")
      if (!msg.startsWith(ID)) throw MessageFormatException("Invalid message identifier!")
      if (msg.last().code != 0x00) throw MessageFormatException("Invalid message stop byte!")
      return RequestPrivateKailleraPortRequest(protocol = msg.substring(ID.length, msg.length - 1))
    }
  }
}
