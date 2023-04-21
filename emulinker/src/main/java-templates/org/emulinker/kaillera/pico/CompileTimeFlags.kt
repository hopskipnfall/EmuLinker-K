package org.emulinker.kaillera.pico

/** Constants inserted at compile time. */
object CompiledFlags {
  const val DEBUG_BUILD: Boolean = ${isDev}
}
