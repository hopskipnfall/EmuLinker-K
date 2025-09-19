package org.emulinker.util

import kotlin.time.Duration

/** A cache used to fetch a single [Long] value on a delay. */
class TimeOffsetCache(delay: Duration, resolution: Duration) {
  private val resolutionNs = resolution.inWholeNanoseconds
  private var lastUpdatedNs: Long? = null

  private val cache: Array<Long?> = arrayOfNulls((delay / resolution).toInt())
  private val cacheSize = cache.size
  private var last: Int = -1
  var size = 0
    private set

  @Synchronized
  fun update(latestVal: Long, nowNs: Long = System.nanoTime()) {
    val lns = lastUpdatedNs
    if (lns == null || nowNs - lns >= resolutionNs) {
      last = Math.floorMod(last + 1, cacheSize)
      cache[last] = latestVal

      if (size < cacheSize) size++

      lastUpdatedNs = nowNs
    }
  }

  @Synchronized
  fun getDelayedValue(): Long? =
    when {
      size == 0 -> null
      size < cacheSize -> cache[0]!!
      else -> cache[(last + 1) % cacheSize]!!
    }

  @Synchronized
  fun clear() {
    last = -1
    size = 0
    lastUpdatedNs = null
  }
}
