package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.Lagometer
import org.emulinker.util.EmuUtil.toMillisDouble
import org.junit.Test

data class PlayerConfiguration(val frameDelay: Int)

class LagoWrapper(val players: List<PlayerConfiguration>, val connectionType: ConnectionType) {

  val l =
    Lagometer(
      frameDurationNs = 1.seconds / 60,
      historyDuration = 1.minutes,
      historyResolution = 1.seconds,
      players = players.map { 1 },
    )

  /** Best approximation of game lag that occurred. */
  val gameLag
    get(): Duration = l.cumulativeLag

  /** How much of the above lag could be definitively attributed to each user. */
  val gameLagPerPlayer
    get() = l.cumulativeGameLagPerPlayer

  private val waitingOnControllerInputsForUser = Array(players.size) { true }

  fun receivedInputsFromUser(nowNs: Long, userIndex: Int) {
    l.receivedInputsFromUser(playerIndex = userIndex, nowNs = nowNs)

    waitingOnControllerInputsForUser[userIndex] = false
  }

  fun advanceFrame(nowNs: Long) {
    assertThat(waitingOnControllerInputsForUser.none { it }).isTrue()
    l.advanceFrame(nowNs = nowNs)

    waitingOnControllerInputsForUser.indices.forEach { waitingOnControllerInputsForUser[it] = true }
  }
}

class LagoWrapperTest {

  @Test
  fun testSinglePlayerNoLag() {
    val lagoWrapper = LagoWrapper(listOf(PlayerConfiguration(1)), ConnectionType.LAN)

    var now = 0L
    repeat(60) {
      now += (1.seconds / 60).inWholeNanoseconds // 60fps = 16.66ms per frame
      lagoWrapper.receivedInputsFromUser(now, 0)
      lagoWrapper.advanceFrame(now)
    }

    assertThat(lagoWrapper.gameLag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagoWrapper.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
  }

  @Test
  fun testSinglePlayerOneFrameDelayWithLag() {
    val lagoWrapper = LagoWrapper(listOf(PlayerConfiguration(1)), ConnectionType.LAN)

    // Simulate lag by sending inputs slower than 60fps
    val repeatTimes = 60

    var now = 0L
    repeat(repeatTimes) {
      now += (1.seconds/50).inWholeNanoseconds
//       now += it * 20_000_000L // 50fps = 20ms per frame
      lagoWrapper.receivedInputsFromUser(now, 0)
    }

    val expectedLag = ((1.seconds / 50) - (1.seconds / 60)) * repeatTimes

    // Expected Lag: (20ms - 16.66ms) * 60 = 200ms
    assertThat(lagoWrapper.gameLag.toMillisDouble()).isWithin(1.0).of(expectedLag.toMillisDouble())
//    assertThat(lagoWrapper.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(expectedLag)
  }

  @Test
  fun testTwoPlayersOneFrameDelayNoLag() {
    val lagoWrapper =
      LagoWrapper(listOf(PlayerConfiguration(1), PlayerConfiguration(1)), ConnectionType.LAN)

    repeat(60) {
      val now = it * 16_666_666L
      lagoWrapper.receivedInputsFromUser(now, 0)
      lagoWrapper.receivedInputsFromUser(now, 1)
    }

    assertThat(lagoWrapper.gameLag.toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagoWrapper.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
    assertThat(lagoWrapper.gameLagPerPlayer[1].toMillisDouble()).isWithin(1.0).of(0.0)
  }

  @Test
  fun testTwoPlayersOneFrameDelayOneLagging() {
    val lagoWrapper =
      LagoWrapper(listOf(PlayerConfiguration(1), PlayerConfiguration(1)), ConnectionType.LAN)

    val repeatTimes = 60
    repeat(repeatTimes) {
      // Player 1 is on time
      lagoWrapper.receivedInputsFromUser(it * 16_666_666L, 0)
      // Player 2 is lagging (20ms instead of 16.66ms)
      lagoWrapper.receivedInputsFromUser(it * 20_000_000L, 1)
    }

    assertThat(lagoWrapper.gameLag.toMillisDouble()).isWithin(1.0).of(200.0)
//    assertThat(lagoWrapper.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(0.0)
//    assertThat(lagoWrapper.gameLagPerPlayer[1].toMillisDouble()).isWithin(1.0).of(200.0)
  }

  @Test
  fun testTwoPlayersOneFrameDelayBothLagging() {
    val lagoWrapper =
      LagoWrapper(listOf(PlayerConfiguration(1), PlayerConfiguration(1)), ConnectionType.LAN)

    repeat(60) {
      // Both players lagging
      val now = it * 20_000_000L
      lagoWrapper.receivedInputsFromUser(now, 0)
      lagoWrapper.receivedInputsFromUser(now, 1)
    }

    assertThat(lagoWrapper.gameLag.toMillisDouble()).isWithin(1.0).of(200.0)
    // Both should be attributed lag
//    assertThat(lagoWrapper.gameLagPerPlayer[0].toMillisDouble()).isWithin(1.0).of(200.0)
//    assertThat(lagoWrapper.gameLagPerPlayer[1].toMillisDouble()).isWithin(1.0).of(200.0)
  }
}
