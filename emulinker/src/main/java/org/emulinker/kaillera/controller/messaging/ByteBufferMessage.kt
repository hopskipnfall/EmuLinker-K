package org.emulinker.kaillera.controller.messaging

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer

abstract class ByteBufferMessage {
  private lateinit var buffer: ByteBuffer

  abstract val bodyBytesPlusMessageIdType: Int

  @Deprecated("Allocate a ByteBuf instead")
  private fun initBuffer(size: Int = bodyBytesPlusMessageIdType) {
    buffer = getBuffer(size)
  }

  @Deprecated("Allocate a ByteBuf instead")
  fun toBuffer(): ByteBuffer {
    initBuffer()
    writeTo(buffer)
    buffer.flip()
    return buffer
  }

  abstract fun writeTo(buffer: ByteBuf)

  abstract fun writeTo(buffer: ByteBuffer)

  companion object {
    @Deprecated("Bad!") fun getBuffer(size: Int): ByteBuffer = ByteBuffer.allocateDirect(size)
  }
}
