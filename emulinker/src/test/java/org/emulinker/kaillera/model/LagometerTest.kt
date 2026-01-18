package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class LagometerTest {
  @Test
  fun `initial lag should be zero`() {
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
    assertThat(lagometer.lag).isLessThan(1.seconds)
  }
}
