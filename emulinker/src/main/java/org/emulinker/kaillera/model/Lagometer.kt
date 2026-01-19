package org.emulinker.kaillera.model

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import org.emulinker.util.TimeOffsetCache

class UserData(
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

class Lagometer(
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

  val userDatas =
    Array(numPlayers) {
      UserData(
        frameDurationNs = frameDurationNs,
        totalDriftCache = TimeOffsetCache(delay = historyDuration, resolution = historyResolution),
      )
    }

  fun receivedInputsFromUser(playerIndex: Int, nowNs: Long) {
    userDatas[playerIndex].receivedDataNs = nowNs
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
