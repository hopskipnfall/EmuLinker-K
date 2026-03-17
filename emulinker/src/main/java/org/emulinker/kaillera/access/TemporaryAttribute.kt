package org.emulinker.kaillera.access

import java.util.Locale
import kotlin.time.Duration
import org.emulinker.util.WildcardStringPattern

sealed class TemporaryAttribute(
  accessStr: String,
  val duration: Duration,
  val issuer: String? = null,
  val reason: String? = null,
) {
  private val patterns: List<WildcardStringPattern> =
    accessStr.lowercase(Locale.getDefault()).split("|").map { WildcardStringPattern(it) }

  private val endTimeMs = System.currentTimeMillis() + duration.inWholeMilliseconds

  val isExpired
    get() = System.currentTimeMillis() > endTimeMs

  fun matches(address: String): Boolean {
    return patterns.any { it.match(address) }
  }
}

class TempBan(
  accessStr: String,
  duration: Duration,
  issuer: String? = null,
  reason: String? = null,
) : TemporaryAttribute(accessStr, duration, issuer, reason)

class TempAdmin(accessStr: String, duration: Duration) : TemporaryAttribute(accessStr, duration)

class TempModerator(accessStr: String, duration: Duration) : TemporaryAttribute(accessStr, duration)

class TempElevated(accessStr: String, duration: Duration) : TemporaryAttribute(accessStr, duration)

class Silence(
  accessStr: String,
  duration: Duration,
  issuer: String? = null,
  reason: String? = null,
) : TemporaryAttribute(accessStr, duration, issuer, reason)
