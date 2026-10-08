package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.emulinker.util.TimeOffsetCache
import org.junit.Test

/**
 * The lag meter arithmetic was rewritten from [Duration] to plain nanosecond longs for speed. This
 * runs both implementations over random input and requires identical results.
 */
class LagometerEquivalenceTest {
  @Test
  fun matchesTheDurationBasedImplementation() {
    val random = Random(7)
    repeat(30) {
      val players = random.nextInt(1, 5)
      val frame = random.nextInt(8, 40).milliseconds
      fun <T> build(make: (Duration, Duration, Duration, Int, Long) -> T) =
        make(frame, 60.seconds, 5.seconds, players, 0L)
      val fast = build { f, h, r, n, s -> Lagometer(f, h, r, n, s) }
      val legacy = build { f, h, r, n, s -> LegacyLagometer(f, h, r, n, s) }

      var now = 0L
      repeat(2000) {
        now += random.nextLong(frame.inWholeNanoseconds / 2, frame.inWholeNanoseconds * 3)
        for (p in 0 until players) {
          if (random.nextInt(4) != 0) {
            val received = now - random.nextLong(0, frame.inWholeNanoseconds)
            fast.receivedInputsFromUser(p, received)
            legacy.receivedInputsFromUser(p, received)
          }
        }
        fast.advanceFrame(now)
        legacy.advanceFrame(now)
        if (random.nextInt(500) == 0) {
          fast.reset()
          legacy.reset()
        }
        assertThat(fast.lag).isEqualTo(legacy.lag)
        assertThat(fast.cumulativeLag).isEqualTo(legacy.cumulativeLag)
        assertThat(fast.gameLagPerPlayer).isEqualTo(legacy.gameLagPerPlayer)
        assertThat(fast.cumulativeGameLagPerPlayer).isEqualTo(legacy.cumulativeGameLagPerPlayer)
      }
    }
  }
}

class LegacyUserData(
  private val frameDurationNs: Duration,
  val totalDriftCache: TimeOffsetCache,
  var receivedDataNs: Long = 0L,
  var totalDrift: Duration = Duration.ZERO,
  var lagLeeway: Duration = Duration.ZERO,
) {

  fun calculateLagForUser(nowNs: Long, lastFrameNs: Long) {
    val delaySinceLastResponseNs = (nowNs - lastFrameNs).nanoseconds
    val timeWaitingNs = (nowNs - receivedDataNs).nanoseconds
    val delaySinceLastResponseMinusWaitingNs = delaySinceLastResponseNs - timeWaitingNs
    val leewayChangeNs = frameDurationNs - delaySinceLastResponseMinusWaitingNs
    lagLeeway += leewayChangeNs
    if (lagLeeway < Duration.ZERO) {
      // Lag leeway fell below zero. We caused lag!
      totalDrift += lagLeeway
      lagLeeway = Duration.ZERO
    } else if (lagLeeway > frameDurationNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeeway = frameDurationNs
    }
    totalDriftCache.update(totalDrift.inWholeNanoseconds, nowNs = nowNs)
  }

  fun reset() {
    totalDriftCache.clear()
    receivedDataNs = 0L
    lagLeeway = Duration.ZERO
  }

  val windowedLag: Duration
    get() =
      (totalDrift - (totalDriftCache.getDelayedValue()?.nanoseconds ?: Duration.ZERO)).absoluteValue
}

class LegacyLagometer(
  val frameDurationNs: Duration,
  historyDuration: Duration,
  historyResolution: Duration,
  val numPlayers: Int,
  startTimeNs: Long,
  private val clock: Clock = Clock.System,
) {
  private var lagLeewayNs: Duration = Duration.ZERO
  private var totalDriftNs: Duration = Duration.ZERO
  private val totalDriftCache =
    TimeOffsetCache(delay = historyDuration, resolution = historyResolution)

  private var lastFrameNs = startTimeNs

  /** The total duration of lag attributed to the game over the history window. */
  val lag: Duration
    get() =
      (totalDriftNs - (totalDriftCache.getDelayedValue()?.nanoseconds ?: Duration.ZERO))
        .absoluteValue

  /** Total cumulative lag since the game started. */
  val cumulativeLag: Duration
    get() = totalDriftNs.absoluteValue

  /** How much of the above lag could be definitively attributed to each user. */
  val gameLagPerPlayer: List<Duration>
    get() = userDatas.map { it.windowedLag }

  /** How much of the above lag could be definitively attributed to each user. */
  val cumulativeGameLagPerPlayer: List<Duration>
    get() = userDatas.map { it.totalDrift.absoluteValue }

  var lastLagReset: Instant = Clock.System.now()

  val userDatas: Array<LegacyUserData> =
    Array(numPlayers) {
      LegacyUserData(
        frameDurationNs = frameDurationNs,
        totalDriftCache = TimeOffsetCache(delay = historyDuration, resolution = historyResolution),
      )
    }

  fun receivedInputsFromUser(playerIndex: Int, nowNs: Long) {
    userDatas.getOrNull(playerIndex)?.receivedDataNs = nowNs
  }

  fun advanceFrame(nowNs: Long) {
    userDatas.forEach { it.calculateLagForUser(nowNs = nowNs, lastFrameNs = lastFrameNs) }

    val delaySinceLastResponseNs = (nowNs - lastFrameNs).nanoseconds

    lagLeewayNs += frameDurationNs - delaySinceLastResponseNs
    if (lagLeewayNs < Duration.ZERO) {
      // Lag leeway fell below zero. Lag occurred!
      totalDriftNs += lagLeewayNs
      lagLeewayNs = Duration.ZERO
    } else if (lagLeewayNs > frameDurationNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeewayNs = frameDurationNs
    }
    totalDriftCache.update(totalDriftNs.inWholeNanoseconds, nowNs = nowNs)
    lastFrameNs = nowNs
  }

  fun reset() {
    totalDriftCache.clear()
    totalDriftNs = Duration.ZERO
    lastLagReset = clock.now()
    userDatas.forEach { it.reset() }
  }
}
