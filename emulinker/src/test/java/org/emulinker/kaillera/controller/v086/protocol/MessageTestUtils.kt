package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.util.EmuUtil.dumpToByteArray

object MessageTestUtils : ProtocolBaseTest() {

  fun assertBufferContainsExactly(buffer: ByteBuffer, byteString: String) {
    val numberWritten = buffer.position()
    val stringForm = buffer.dumpToByteArray().take(numberWritten).toByteArray().toHexString()

    assertThat(stringForm).isEqualTo(byteString.replace(" ", "").replace(",", "").lowercase())

    stringForm.split(",").drop(numberWritten).forEach { assertThat(it).isEqualTo("00") }
  }

  fun assertBufferContainsExactly(buffer: ByteBuf, byteString: String) {
    assertThat(buffer.dumpToByteArray().toHexString())
      .isEqualTo(byteString.replace(" ", "").replace(",", "").lowercase())
  }
}
