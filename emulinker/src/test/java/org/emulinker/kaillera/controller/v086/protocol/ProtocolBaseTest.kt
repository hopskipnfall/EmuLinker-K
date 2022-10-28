package org.emulinker.kaillera.controller.v086.protocol

import java.nio.charset.Charset
import org.emulinker.kaillera.pico.AppModule
import org.junit.BeforeClass

open class ProtocolBaseTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }
  }
}
