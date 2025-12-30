package org.emulinker.kaillera.model

import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.emulinker.util.TimeOffsetCache

/**
 * Encapsulates the logic for measuring lag (drift) against a expected frame rate.
 *
 * This class tracks the "drift" of incoming events (like game data packets) relative to the
 * expected arrival time based on a constant frame rate. It accounts for a "leeway" buffer that
 * allows for some jitter without counting as lag, which scales with the user's frame delay setting.
 */
class Lagometer(
  val frameDurationNs: Long,
  historyDuration: Duration = 15.seconds,
  historyResolution: Duration = 5.seconds,
) {
  private var lagLeewayNs = 0L
  private var totalDriftNs = 0L
  private val totalDriftCache =
    TimeOffsetCache(delay = historyDuration, resolution = historyResolution)

  /** The total duration of lag attributed to the user/game over the history window. */
  val lag: Duration
    get() = (totalDriftNs - (totalDriftCache.getDelayedValue() ?: 0)).nanoseconds.absoluteValue

  /**
   * Updates the lag measurement based on the time elapsed since the last expected event.
   *
   * @param delaySinceLastResponseNs The actual time elapsed since the last event (or frame
   *   boundary).
   * @param minFrameDelay The minimum frame delay (buffer size in frames) currently applicable.
   *   Drift within this buffer is absorbed and not counted as lag.
   */
  fun update(delaySinceLastResponseNs: Long, minFrameDelay: Int, nowNs: Long) {
    // We treat '0' as "Buffer Full".
    // We allow the buffer to drain down to negative values up to -maxLagLeewayNs.
    // Any drop below -maxLagLeewayNs is lag.

    val maxLagLeewayNs = frameDurationNs * (minFrameDelay + 1)

    // replenishing the leeway
    // If delay is small (e.g. 0), we add frameDurationNs. Leeway goes up (towards 0).
    // If delay is large, we subtract. Leeway goes down (negative).
    lagLeewayNs += frameDurationNs - delaySinceLastResponseNs

    if (lagLeewayNs < -maxLagLeewayNs) {
      // Buffer underflow. Lag occurred!
      val overflow = lagLeewayNs - (-maxLagLeewayNs)
      totalDriftNs += overflow
      lagLeewayNs = -maxLagLeewayNs
    } else if (lagLeewayNs > 0) {
      // Buffer overflow. We are ahead of schedule. Cap at 0 (Full).
      lagLeewayNs = 0
    }

    totalDriftCache.update(totalDriftNs, nowNs = nowNs)
  }

  fun reset() {
    totalDriftNs = 0
    lagLeewayNs = 0
    totalDriftCache.clear()
  }
}
