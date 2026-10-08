package org.emulinker.kaillera.model

import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import org.emulinker.util.TimeOffsetCache

/**
 * Lag bookkeeping for one player.
 *
 * [calculateLagForUser] runs for every player on every frame, so the arithmetic is done on plain
 * nanosecond [Long]s rather than [Duration] values, whose operations do extra range handling.
 */
class UserData(
  frameDurationNs: Duration,
  val totalDriftCache: TimeOffsetCache,
  var receivedDataNs: Long = 0L,
) {
  private val frameNs = frameDurationNs.inWholeNanoseconds
  private var lagLeewayNs = 0L
  private var totalDriftNs = 0L

  /** Cumulative drift (negative: lag this player caused). */
  val totalDrift: Duration
    get() = totalDriftNs.nanoseconds

  fun calculateLagForUser(nowNs: Long, lastFrameNs: Long) {
    val delaySinceLastResponseNs = nowNs - lastFrameNs
    val timeWaitingNs = nowNs - receivedDataNs
    lagLeewayNs += frameNs - (delaySinceLastResponseNs - timeWaitingNs)
    if (lagLeewayNs < 0) {
      // Lag leeway fell below zero. We caused lag!
      totalDriftNs += lagLeewayNs
      lagLeewayNs = 0
    } else if (lagLeewayNs > frameNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeewayNs = frameNs
    }
    totalDriftCache.update(totalDriftNs, nowNs = nowNs)
  }

  fun reset() {
    totalDriftCache.clear()
    receivedDataNs = 0L
    lagLeewayNs = 0L
  }

  val windowedLag: Duration
    get() = abs(totalDriftNs - (totalDriftCache.getDelayedValue() ?: 0L)).nanoseconds
}

class Lagometer(
  val frameDurationNs: Duration,
  historyDuration: Duration,
  historyResolution: Duration,
  val numPlayers: Int,
  startTimeNs: Long,
  private val clock: Clock = Clock.System,
) {
  private val frameNs = frameDurationNs.inWholeNanoseconds
  private var lagLeewayNs = 0L
  private var totalDriftNs = 0L
  private val totalDriftCache =
    TimeOffsetCache(delay = historyDuration, resolution = historyResolution)

  private var lastFrameNs = startTimeNs

  /** The total duration of lag attributed to the game over the history window. */
  val lag: Duration
    get() = abs(totalDriftNs - (totalDriftCache.getDelayedValue() ?: 0L)).nanoseconds

  /** Total cumulative lag since the game started. */
  val cumulativeLag: Duration
    get() = abs(totalDriftNs).nanoseconds

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
    userDatas.getOrNull(playerIndex)?.receivedDataNs = nowNs
  }

  fun advanceFrame(nowNs: Long) {
    for (userData in userDatas) {
      userData.calculateLagForUser(nowNs = nowNs, lastFrameNs = lastFrameNs)
    }

    lagLeewayNs += frameNs - (nowNs - lastFrameNs)
    if (lagLeewayNs < 0) {
      // Lag leeway fell below zero. Lag occurred!
      totalDriftNs += lagLeewayNs
      lagLeewayNs = 0
    } else if (lagLeewayNs > frameNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeewayNs = frameNs
    }
    totalDriftCache.update(totalDriftNs, nowNs = nowNs)
    lastFrameNs = nowNs
  }

  fun reset() {
    totalDriftCache.clear()
    totalDriftNs = 0L
    lastLagReset = clock.now()
    userDatas.forEach { it.reset() }
  }
}
