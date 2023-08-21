package org.emulinker.kaillera.controller.v086.protocol

import java.nio.charset.Charset
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.BeforeClass
import org.junit.Rule

open class ProtocolBaseTest {
  @get:Rule val logging = LoggingRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }
  }
}
