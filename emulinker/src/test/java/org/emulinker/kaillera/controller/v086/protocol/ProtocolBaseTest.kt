package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.Charset
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.BeforeClass
import org.junit.Rule

abstract class ProtocolBaseTest {
  @get:Rule val logging = LoggingRule()

  protected fun allocateByteBuffer() = ByteBuffer.allocate(4096).order(LITTLE_ENDIAN)

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }
  }
}
