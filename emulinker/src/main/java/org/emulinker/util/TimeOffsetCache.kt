package org.emulinker.util

import kotlin.time.Duration

/**
 * A cache used to fetch a single [Long] value on a delay.
 *
 * Not thread safe: use from one thread, or guard externally.
 */
class TimeOffsetCache(delay: Duration, resolution: Duration) {
  init {
    require(resolution.isPositive()) { "resolution must be positive" }
  }

  private val resolutionNs = resolution.inWholeNanoseconds
  private var lastUpdatedNs: Long? = null

  private val cache: Array<Long?> = arrayOfNulls(maxOf(1, (delay / resolution).toInt()))
  private val cacheSize = cache.size
  private var last: Int = -1
  var size = 0
    private set

  fun update(latestVal: Long, nowNs: Long = System.nanoTime()) {
    val lns = lastUpdatedNs
    if (lns == null || nowNs - lns >= resolutionNs) {
      last = Math.floorMod(last + 1, cacheSize)
      cache[last] = latestVal

      if (size < cacheSize) size++

      lastUpdatedNs = nowNs
    }
  }

  fun getDelayedValue(): Long? =
    when {
      // If empty or there is only one element, return null (no deferred value available).
      size <= 1 -> null
      size < cacheSize -> cache[0]!!
      else -> cache[(last + 1) % cacheSize]!!
    }

  fun clear() {
    last = -1
    size = 0
    lastUpdatedNs = null
  }
}
