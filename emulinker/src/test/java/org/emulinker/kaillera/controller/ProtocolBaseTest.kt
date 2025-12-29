package org.emulinker.kaillera.controller

import java.nio.charset.Charset
import org.junit.BeforeClass

abstract class ProtocolBaseTest {
  companion object {
    protected const val MESSAGE_NUMBER = 42

    lateinit var globalCharset: Charset

    @BeforeClass
    @JvmStatic
    fun setup() {
      globalCharset = Charset.forName("Shift_JIS")
    }
  }
}
