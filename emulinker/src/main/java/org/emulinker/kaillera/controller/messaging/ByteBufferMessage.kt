package org.emulinker.kaillera.controller.messaging

import java.nio.ByteBuffer

abstract class ByteBufferMessage {
  private lateinit var buffer: ByteBuffer

  abstract val bodyBytesPlusMessageIdType: Int

  private fun initBuffer() {
    initBuffer(bodyBytesPlusMessageIdType)
  }

  private fun initBuffer(size: Int) {
    buffer = getBuffer(size)
  }

  fun toBuffer(): ByteBuffer {
    initBuffer()
    writeTo(buffer)
    buffer.flip()
    return buffer
  }

  abstract fun writeTo(buffer: ByteBuffer)

  companion object {
    fun getBuffer(size: Int): ByteBuffer {
      return ByteBuffer.allocateDirect(size)
    }
  }
}
