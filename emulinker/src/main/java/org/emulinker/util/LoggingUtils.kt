package org.emulinker.util

import org.emulinker.kaillera.pico.CompiledFlags

/**
 * Wraps code that should not appear in the production binary.
 *
 * The intended use case is to wrap logging/debug code to optimize performance-critical paths.
 */
inline fun stripFromProdBinary(codeBlock: () -> Unit) {
  if (CompiledFlags.DEBUG_BUILD) {
    codeBlock()
  }
}
