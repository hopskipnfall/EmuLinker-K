package org.emulinker.util

import java.nio.charset.Charset

/**
 * The character set used to encode and decode every string on the wire (user names, chat, game
 * names, ...).
 *
 * This is process-wide configuration: it is set once at startup from `emulinker.charset` and never
 * changes while the server runs. It is held here, instead of being injected, because wire messages
 * and their serializers are plain objects created far from the dependency graph. Everything that
 * needs the charset reads [value]; nothing else should store its own copy.
 */
object ProtocolCharset {
  @Volatile private var charset: Charset? = null

  /** The configured charset. Fails fast if [initialize] has not been called yet. */
  val value: Charset
    get() = checkNotNull(charset) { "ProtocolCharset has not been initialized." }

  /** Called once at startup after the configuration has been read. */
  fun initialize(charset: Charset) {
    this.charset = charset
  }

  /** Overrides the charset for a test. Production code must use [initialize]. */
  fun overrideForTesting(charset: Charset) {
    this.charset = charset
  }
}
