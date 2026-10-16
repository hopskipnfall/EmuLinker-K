package org.emulinker.kaillera.access

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * Tracks how fast each source address logs in and how often it is refused, so that one machine
 * cannot flood the server with users (for example an unauthorized load test).
 *
 * Addresses are grouped with [keyOf]: IPv4 addresses individually and IPv6 addresses by /64, since
 * a single home connection is routinely handed billions of IPv6 addresses.
 *
 * Thread safe. [nanoTime] is injectable for tests.
 */
class ConnectionGuard(
  /** Logins allowed per address per minute; 0 disables the rate limit. */
  private val maxLoginsPerMinute: Int,
  /** Refusals within a minute after which [recordViolation] says to ban; 0 never bans. */
  private val violationsBeforeBan: Int,
  private val nanoTime: () -> Long = System::nanoTime,
) {
  private class Entry(var tokens: Double, var lastRefillNs: Long) {
    var violations = 0
    var violationWindowStartNs = lastRefillNs
  }

  private val entries = ConcurrentHashMap<String, Entry>()

  /**
   * Consumes one login token for [key].
   *
   * @return false if the address has used up its allowance and the login should be refused.
   */
  fun allowLogin(key: String): Boolean {
    if (maxLoginsPerMinute <= 0) return true
    val now = nanoTime()
    val entry = entryFor(key, now)
    synchronized(entry) {
      val capacity = maxLoginsPerMinute.toDouble()
      val refill = (now - entry.lastRefillNs) / MINUTE_NS * capacity
      entry.tokens = minOf(capacity, entry.tokens + refill)
      entry.lastRefillNs = now
      if (entry.tokens < 1.0) return false
      entry.tokens -= 1.0
      return true
    }
  }

  /**
   * Records that a login from [key] was refused.
   *
   * @return true if the address has now been refused [violationsBeforeBan] times within a minute
   *   and should be banned. The count restarts afterwards.
   */
  fun recordViolation(key: String): Boolean {
    if (violationsBeforeBan <= 0) return false
    val now = nanoTime()
    val entry = entryFor(key, now)
    synchronized(entry) {
      if (now - entry.violationWindowStartNs > MINUTE_NS) {
        entry.violations = 0
        entry.violationWindowStartNs = now
      }
      entry.violations++
      if (entry.violations >= violationsBeforeBan) {
        entry.violations = 0
        entry.violationWindowStartNs = now
        return true
      }
      return false
    }
  }

  /** Forgets addresses that have been quiet long enough for their allowance to be full again. */
  fun purgeIdle() {
    val now = nanoTime()
    entries.entries.removeIf { (_, e) -> synchronized(e) { now - e.lastRefillNs > IDLE_NS } }
  }

  private fun entryFor(key: String, now: Long): Entry {
    entries[key]?.let {
      return it
    }
    if (entries.size >= MAX_TRACKED_ADDRESSES) purgeIdle()
    // Still full of recently active addresses (for example a flood from many forged sources): do
    // not grow without bound and do not wipe the limits of the addresses already tracked. A new
    // address is simply not limited until there is room.
    if (entries.size >= MAX_TRACKED_ADDRESSES) return Entry(maxLoginsPerMinute.toDouble(), now)
    return entries.computeIfAbsent(key) { Entry(maxLoginsPerMinute.toDouble(), now) }
  }

  companion object {
    private val MINUTE_NS = 1.minutes.inWholeNanoseconds.toDouble()
    private val IDLE_NS = 5.minutes.inWholeNanoseconds
    private const val MAX_TRACKED_ADDRESSES = 20_000

    /** Groups [address] for counting: the address itself for IPv4, the /64 prefix for IPv6. */
    fun keyOf(address: InetAddress): String =
      when {
        address is Inet4Address -> address.hostAddress
        address is Inet6Address -> {
          val bytes = address.address
          "v6:" +
            (0 until 4).joinToString(":") { i ->
              "%x"
                .format(
                  ((bytes[2 * i].toInt() and 0xFF) shl 8) or (bytes[2 * i + 1].toInt() and 0xFF)
                )
            }
        }
        else -> address.hostAddress
      }

    /**
     * The wildcard pattern (as used in `access.cfg`) matching every address that shares [key]'s
     * group, for banning the whole group.
     */
    fun banPatternFor(address: InetAddress): String {
      val key = keyOf(address)
      return if (key.startsWith("v6:")) key.removePrefix("v6:") + ":*" else key
    }
  }
}
