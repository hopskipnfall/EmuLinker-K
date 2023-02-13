package org.emulinker.util

object LoggingUtils {
  const val DEBUG_LOGGING_ENABLED = false

  /**
   * Wraps logging code that will not appear in the compiled binary if [DEBUG_LOGGING_ENABLED] is
   * false.
   */
  inline fun debugLog(logBlock: () -> Unit) {
    if (DEBUG_LOGGING_ENABLED) {
      logBlock()
    }
  }
}
