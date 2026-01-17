package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.emulinker.util.EmuUtil.toMillisDouble
import org.junit.Test

class LagoWrapperTest {

  @Test
  fun testSinglePlayerNoLag() {
    val lagometer = buildLagometer(1)

    var now = 0L
    repeat(60) {
      now += (1.seconds / 60).inWholeNanoseconds
      lagometer.receivedInputsFromUser(0, now)
      lagometer.advanceFrame(now)
    }

    assertThat(lagometer.cumulativeLag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.lag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.cumulativeGameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
  }

  @Test
  fun testSinglePlayerOneFrameDelayWithLag() {
    val lagometer = buildLagometer(1)

    // Simulate lag by sending inputs slower than 60fps
    val repeatTimes = 60

    var now = 0L
    repeat(repeatTimes) {
      now += (1.seconds / 50).inWholeNanoseconds
      lagometer.receivedInputsFromUser(0, now)
      lagometer.advanceFrame(now)
    }

    val expectedLag = ((1.seconds / 50) - (1.seconds / 60)) * repeatTimes

    // Expected Lag: (20ms - 16.66ms) * 60 = 200ms
    assertThat(lagometer.cumulativeLag.toMillisDouble())
      .isWithin(1.0)
      .of(expectedLag.toMillisDouble())
    assertThat(lagometer.cumulativeGameLagPerPlayer[0].toMillisDouble())
      .isWithin(1.0)
      .of(expectedLag.toMillisDouble())
  }

  @Test
  fun testTwoPlayersOneFrameDelayNoLag() {
    val lagometer = buildLagometer(2)

    repeat(60) {
      val now = it * 16_666_666L
      lagometer.receivedInputsFromUser(0, now)
      lagometer.receivedInputsFromUser(1, now)
    }

    assertThat(lagometer.cumulativeLag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.lag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.cumulativeGameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.cumulativeGameLagPerPlayer[1].toMillisDouble()).isWithin(1.0).of(0.0)

    assertThat(lagometer.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.gameLagPerPlayer[1].toMillisDouble()).isWithin(1.0).of(0.0)
  }

  @Test
  fun testTwoPlayersOneFrameDelayOneLagging() {
    val lagometer = buildLagometer(2)

    var now = Duration.Companion.ZERO
    val repeatTimes = 60
    repeat(repeatTimes) {
      now += 1.seconds / 60

      // Player 1 is on time.
      lagometer.receivedInputsFromUser(0, now.inWholeNanoseconds)

      // Player 2 is lagging 10ms per frame.
      now += 10.milliseconds
      lagometer.receivedInputsFromUser(1, now.inWholeNanoseconds)
      lagometer.advanceFrame(now.inWholeNanoseconds)
    }

    val expectedLag = 10.milliseconds * repeatTimes
    assertThat(lagometer.cumulativeLag.toMillisDouble())
      .isWithin(1.0)
      .of(expectedLag.toMillisDouble())
    assertThat(lagometer.cumulativeGameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.cumulativeGameLagPerPlayer[1].toMillisDouble())
      .isWithin(1.0)
      .of(expectedLag.toMillisDouble())

    assertThat(lagometer.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagometer.gameLagPerPlayer[1].toMillisDouble())
      .isWithin(1.0)
      .of(expectedLag.toMillisDouble())
  }

  @Test
  fun testTwoPlayersOneFrameDelayBothLagging() {
    val lagometer = buildLagometer(2)

    var now = Duration.Companion.ZERO
    val repeatTimes = 60
    repeat(repeatTimes) {
      now += 1.seconds / 60

      // Player 1 is lagging 5ms per frame.
      now += 5.milliseconds
      lagometer.receivedInputsFromUser(0, now.inWholeNanoseconds)

      // Player 2 is lagging 10ms per frame.
      now += 5.milliseconds
      lagometer.receivedInputsFromUser(1, now.inWholeNanoseconds)
      lagometer.advanceFrame(now.inWholeNanoseconds)
    }

    assertThat(lagometer.cumulativeLag.toMillisDouble())
      .isWithin(1.0)
      .of((10.milliseconds * repeatTimes).toMillisDouble())
    assertThat(lagometer.lag.toMillisDouble())
      .isWithin(1.0)
      .of((10.milliseconds * repeatTimes).toMillisDouble())
    // Both should be attributed lag
    assertThat(lagometer.cumulativeGameLagPerPlayer[0].toMillisDouble())
      .isWithin(1.0)
      .of((5.milliseconds * repeatTimes).toMillisDouble())
    assertThat(lagometer.cumulativeGameLagPerPlayer[1].toMillisDouble())
      .isWithin(1.0)
      .of((10.milliseconds * repeatTimes).toMillisDouble())

    assertThat(lagometer.gameLagPerPlayer[0].toMillisDouble())
      .isWithin(1.0)
      .of((5.milliseconds * repeatTimes).toMillisDouble())
    assertThat(lagometer.gameLagPerPlayer[1].toMillisDouble())
      .isWithin(1.0)
      .of((10.milliseconds * repeatTimes).toMillisDouble())
  }

  private companion object {
    fun buildLagometer(numPlayers: Int) =
      Lagometer(
        frameDurationNs = 1.seconds / 60,
        historyDuration = 1.minutes,
        historyResolution = 30.seconds,
        numPlayers = numPlayers,
      )
  }
}
