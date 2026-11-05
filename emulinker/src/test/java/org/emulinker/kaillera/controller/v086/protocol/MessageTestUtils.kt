package org.emulinker.kaillera.controller.v086.protocol

import io.kotest.matchers.shouldBe
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.util.EmuUtil.dumpToByteArray

object MessageTestUtils {

  fun assertBufferContainsExactly(buffer: ByteBuffer, byteString: String) {
    val numberWritten = buffer.position()
    val stringForm = buffer.dumpToByteArray().take(numberWritten).toByteArray().toHexString()

    stringForm shouldBe byteString.replace(" ", "").replace(",", "").lowercase()

    stringForm.split(",").drop(numberWritten).forEach { it shouldBe "00" }
  }

  fun assertBufferContainsExactly(buffer: ByteBuf, byteString: String) {
    buffer.dumpToByteArray().toHexString() shouldBe
      byteString.replace(" ", "").replace(",", "").lowercase()
  }
}
