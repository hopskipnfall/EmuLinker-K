package org.emulinker.kaillera.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LagometerTest :
  FunSpec({
    test("initial lag should be zero") {
      val nowNs = System.nanoTime()
      val lagometer =
        Lagometer(
          frameDurationNs = 16.milliseconds,
          historyDuration = 60.seconds,
          historyResolution = 5.seconds,
          numPlayers = 2,
          startTimeNs = nowNs,
        )

      lagometer.advanceFrame(nowNs)

      // This should be small, definitely not years or minutes
      lagometer.lag shouldBeLessThan 1.seconds
    }
  })
