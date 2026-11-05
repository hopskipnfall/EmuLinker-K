package org.emulinker.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import kotlin.time.Duration.Companion.seconds

class TimeOffsetCacheEdgeCaseTest :
  FunSpec({
    test("delayShorterThanResolutionDoesNotThrow") {
      val cache = TimeOffsetCache(delay = 1.seconds, resolution = 5.seconds)

      cache.update(1, nowNs = 0)
      cache.update(2, nowNs = 6_000_000_000)

      cache.getDelayedValue().shouldBeNull()
    }

    test("zeroResolutionIsRejected") {
      shouldThrow<IllegalArgumentException> {
        TimeOffsetCache(delay = 1.seconds, resolution = 0.seconds)
      }
    }
  })
