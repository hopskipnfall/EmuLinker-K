package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning

object MessageTestUtils : ProtocolBaseTest() {

  fun assertBufferContainsExactly(buffer: ByteBuffer, byteString: String) {
    val numberWritten = buffer.position()
    val stringForm = buffer.dumpBufferFromBeginning()

    assertThat(stringForm.split(",").take(numberWritten))
      .isEqualTo(byteString.replace(" ", "").split(","))

    stringForm.split(",").drop(numberWritten).forEach { assertThat(it).isEqualTo("00") }
  }

  fun assertBufferContainsExactly(buffer: ByteBuf, byteString: String) {
    val readableBytes = buffer.readableBytes()
    val nio = buffer.nioBuffer()
    nio.order(ByteOrder.LITTLE_ENDIAN)
    val stringForm = nio.dumpBufferFromBeginning()

    assertThat(stringForm.split(",")).isEqualTo(byteString.replace(" ", "").split(","))

    stringForm.split(",").drop(readableBytes).forEach { assertThat(it).isEqualTo("00") }
  }
}
