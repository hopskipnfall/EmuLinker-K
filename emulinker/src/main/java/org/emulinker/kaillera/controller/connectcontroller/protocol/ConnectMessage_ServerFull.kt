package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import kotlin.Throws
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.util.EmuUtil

/** Server Full Response. */
object ConnectMessage_ServerFull : ConnectMessage() {
  override val iD = ID

  override fun toString() = "Server Full Response"

  override val bodyBytesPlusMessageIdType: Int
    get() = ID.length + 1

  override fun writeTo(buffer: ByteBuf) {
    EmuUtil.writeString(buffer, ID)
  }

  override fun writeTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, ID)
  }

  const val ID = "TOO"

  @Throws(MessageFormatException::class)
  fun parse(msg: String): ConnectMessage {
    if (msg.length != ID.length + 1) throw MessageFormatException("Invalid message length!")
    if (!msg.startsWith(ID)) throw MessageFormatException("Invalid message identifier!")
    if (msg[msg.length - 1].code != 0x00) throw MessageFormatException("Invalid message stop byte!")
    return ConnectMessage_ServerFull
  }
}
