package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertThrows
import org.junit.Test

class TimeOffsetCacheEdgeCaseTest {
  @Test
  fun delayShorterThanResolutionDoesNotThrow() {
    val cache = TimeOffsetCache(delay = 1.seconds, resolution = 5.seconds)

    cache.update(1, nowNs = 0)
    cache.update(2, nowNs = 6_000_000_000)

    assertThat(cache.getDelayedValue()).isNull()
  }

  @Test
  fun zeroResolutionIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      TimeOffsetCache(delay = 1.seconds, resolution = 0.seconds)
    }
  }
}
