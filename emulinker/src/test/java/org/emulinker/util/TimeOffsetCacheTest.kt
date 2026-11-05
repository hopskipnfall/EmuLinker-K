package org.emulinker.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.nanoseconds

class TimeOffsetCacheTest :
  FunSpec({
    test("cache and retrieve single value") {
      val target = TimeOffsetCache(delay = 30.nanoseconds, resolution = 10.nanoseconds)

      repeat(2) { target.update(latestVal = it.toLong(), nowNs = 10L * it) }

      target.getDelayedValue() shouldBe 0L
    }

    test("single value cache should return null") {
      val target = TimeOffsetCache(delay = 100.nanoseconds, resolution = 10.nanoseconds)

      target.update(latestVal = 42, nowNs = 0)

      target.getDelayedValue().shouldBeNull()
    }

    test("update should ignore new values within resolution") {
      val target = TimeOffsetCache(delay = 100.nanoseconds, resolution = 10.nanoseconds)

      target.update(latestVal = 42, nowNs = 0)
      target.update(latestVal = 43, nowNs = 10)

      // Add one more dummy value.
      target.update(latestVal = 100, nowNs = 10)

      target.getDelayedValue() shouldBe 42L
    }

    test("retrieve from full cache") {
      val target = TimeOffsetCache(delay = 30.nanoseconds, resolution = 10.nanoseconds)

      repeat(100) { target.update(latestVal = it.toLong(), nowNs = 10L * it) }

      // The last values we put were 99, 98, 97.
      target.getDelayedValue() shouldBe 97L
    }
  })
