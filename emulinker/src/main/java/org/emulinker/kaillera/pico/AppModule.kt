package org.emulinker.kaillera.pico

import java.nio.charset.Charset

abstract class AppModule {

  companion object {
    // TODO(nue): Burn this with fire!!!
    // NOTE: This is NOT marked final and there are race conditions involved. Inject @RuntimeFlags
    // instead!
    lateinit var charsetDoNotUse: Charset

    // TODO(nue): Clean this up.
    /**
     * Messages to be shown to admins as they log in.
     *
     * Usually used for update messages.
     */
    var messagesToAdmins: List<String> = emptyList()
  }
}
