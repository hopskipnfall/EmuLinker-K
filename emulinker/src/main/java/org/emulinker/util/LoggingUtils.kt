package org.emulinker.util

import org.emulinker.kaillera.pico.CompiledFlags

object LoggingUtils {
  /**
   * Wraps logging code that will not appear in the compiled binary if [CompiledFlags.DEBUG_BUILD]
   * is false.
   */
  inline fun debugLog(logBlock: () -> Unit) {
    if (CompiledFlags.DEBUG_BUILD) {
      logBlock()
    }
  }
}
