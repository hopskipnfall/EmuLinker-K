package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.util.EmuUtil.dumpBufferFromBeginning

object MessageTestUtils {

  fun assertBufferContainsExactly(buffer: ByteBuffer, byteString: String) {
    val numberWritten = buffer.position()
    val stringForm = buffer.dumpBufferFromBeginning()

    assertThat(stringForm.split(",").take(numberWritten))
      .isEqualTo(byteString.replace(" ", "").split(","))

    stringForm.split(",").drop(numberWritten).forEach { assertThat(it).isEqualTo("00") }
  }
}
