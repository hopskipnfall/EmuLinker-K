package org.emulinker.kaillera.controller.messaging

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer

interface ByteBufferMessage {
  val bodyBytesPlusMessageIdType: Int

  fun writeTo(buffer: ByteBuf)

  fun writeTo(buffer: ByteBuffer)
}
