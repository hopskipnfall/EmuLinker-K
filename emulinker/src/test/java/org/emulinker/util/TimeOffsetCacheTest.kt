package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.nanoseconds
import org.junit.Test

class TimeOffsetCacheTest {

  @Test
  fun `cache and retrieve single value`() {
    val target = TimeOffsetCache(delay = 100.nanoseconds, resolution = 10.nanoseconds)

    target.update(latestVal = 42, nowNs = 0)

    assertThat(target.getDelayedValue()).isEqualTo(42)
  }

  @Test
  fun `update should ignore new values within resolution`() {
    val target = TimeOffsetCache(delay = 100.nanoseconds, resolution = 10.nanoseconds)

    target.update(latestVal = 42, nowNs = 0)
    target.update(latestVal = 100, nowNs = 9)

    assertThat(target.getDelayedValue()).isEqualTo(42)
  }

  @Test
  fun `retrieve from full cache`() {
    val target = TimeOffsetCache(delay = 30.nanoseconds, resolution = 10.nanoseconds)

    repeat(100) { target.update(latestVal = it.toLong(), nowNs = 10L * it) }

    // The last values we put were 99, 98, 97.
    assertThat(target.getDelayedValue()).isEqualTo(97)
  }
}
