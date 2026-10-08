package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.util.EmuUtil
import org.emulinker.util.ProtocolCharset

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
    buffer.writeBytes(ProtocolCharset.value.encode(iD))
    EmuUtil.writeString(buffer, protocol, 0x00, ProtocolCharset.value)
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
