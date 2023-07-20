package org.emulinker.kaillera.controller.v086.protocol

import java.nio.charset.Charset
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.BeforeClass
import org.junit.Rule

open class ProtocolBaseTest {

  @get:Rule
  val logging =
    LoggingRule(
      V086Utils::class,
      *ServerMessage::class.sealedSubclasses.toTypedArray(),
      *ClientMessage::class.sealedSubclasses.toTypedArray(),
    )

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }
  }
}
