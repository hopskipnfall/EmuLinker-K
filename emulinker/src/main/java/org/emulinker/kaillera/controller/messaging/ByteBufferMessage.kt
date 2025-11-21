package org.emulinker.kaillera.controller.messaging

import io.netty.buffer.ByteBuf

interface ByteBufferMessage {
  val bodyBytesPlusMessageIdType: Int

  fun writeTo(buffer: ByteBuf)
}
