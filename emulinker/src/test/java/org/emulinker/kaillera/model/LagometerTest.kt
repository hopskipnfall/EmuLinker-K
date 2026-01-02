package org.emulinker.kaillera.model

import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LagometerTest {
  @Test
  fun `no lag when updates match frame duration`() {
    val frameDurationNs = 16.milliseconds.inWholeNanoseconds
    val lagometer = Lagometer(frameDurationNs)
    var now = 0L

    for (i in 1..100) {
      now += frameDurationNs
      lagometer.update(frameDurationNs, minFrameDelay = 1, nowNs = now)
    }

    assertEquals(0.milliseconds, lagometer.lag)
  }

  @Test
  fun `no lag when jitter is within buffer`() {
    val frameDurationNs = 16.milliseconds.inWholeNanoseconds
    val lagometer = Lagometer(frameDurationNs)
    var now = 0L

    // minFrameDelay = 1 means buffer is 2 * frameDuration
    // We can handle jitter up to that amount

    // Late arrival (stalled for 1.5 frames)
    now += (frameDurationNs * 1.5).toLong()
    lagometer.update((frameDurationNs * 1.5).toLong(), minFrameDelay = 1, nowNs = now)
    assertEquals(0.milliseconds, lagometer.lag)

    // Catch up (0.5 frames)
    now += (frameDurationNs * 0.5).toLong()
    lagometer.update((frameDurationNs * 0.5).toLong(), minFrameDelay = 1, nowNs = now)
    assertEquals(0.milliseconds, lagometer.lag)
  }

  @Test
  fun `lag reported when stall exceeds buffer`() {
    val frameDurationNs = 16.milliseconds.inWholeNanoseconds
    val lagometer = Lagometer(frameDurationNs, historyResolution = 1.milliseconds)
    var now = 0L

    // minFrameDelay = 1 -> Max Leeway = 2 frames

    // Establish baseline at T=0
    lagometer.update(0, 1, 0)

    // Stall for 4 frames
    val delay = frameDurationNs * 4
    now += delay
    lagometer.update(delay, minFrameDelay = 1, nowNs = now)

    // Expected Lag: Delay - (Leeway + Credit)
    // Credit = 1 frame.
    // Net Deficit = 3frames.
    // Buffer = 2 frames.
    // Lag = 1 frame.
    val expectedLag = 16.milliseconds
    // Floating point precision might be slight off, but Duration handles it fairly well
    // Allow small epsilon if needed, but here we work with Long ns
    assertEquals(expectedLag, lagometer.lag)
  }

  @Test
  fun `higher frame delay increases buffer`() {
    val frameDurationNs = 16.milliseconds.inWholeNanoseconds
    val lagometer = Lagometer(frameDurationNs)
    var now = 0L

    // minFrameDelay = 5 -> Max Leeway = 6 frames

    // Stall for 5 frames (should be absorbed)
    val delay = frameDurationNs * 5
    now += delay
    lagometer.update(delay, minFrameDelay = 5, nowNs = now)

    assertEquals(0.milliseconds, lagometer.lag)
  }

  @Test
  fun `lag attribution sum does not exceed game lag 1v1`() {
    checkLagAttribution(p1FrameDelay = 1, p2FrameDelay = 1)
  }

  @Test
  fun `lag attribution sum does not exceed game lag 2v2`() {
    checkLagAttribution(p1FrameDelay = 2, p2FrameDelay = 2)
  }

  @Test
  fun `lag attribution sum does not exceed game lag 1v2`() {
    checkLagAttribution(p1FrameDelay = 1, p2FrameDelay = 2)
  }

  @Test
  fun `connection type LAN (60Hz)`() {
    checkConnectionType(byteValue = 1) // 1 frame per update
  }

  @Test
  fun `connection type GOOD (20Hz)`() {
    checkConnectionType(byteValue = 3) // 3 frames per update
  }

  @Test
  fun `connection type BAD (10Hz)`() {
    checkConnectionType(byteValue = 6) // 6 frames per update
  }

  private fun checkConnectionType(byteValue: Int) {
    val baseFrameDurationNs = 16.milliseconds.inWholeNanoseconds
    val expectedIntervalNs = baseFrameDurationNs * byteValue

    val lagometer = Lagometer(expectedIntervalNs, historyResolution = 1.milliseconds)
    var now = 0L

    // Initial baseline
    lagometer.update(0, minFrameDelay = 1, nowNs = now)

    // Simulate steady state
    for (i in 1..50) {
      now += expectedIntervalNs
      lagometer.update(expectedIntervalNs, minFrameDelay = 1, nowNs = now)
    }
    assertEquals(0.milliseconds, lagometer.lag, "Steady state should have 0 lag")

    // Simulate lag spike (stall for 10 full intervals + buffer)
    val stallDuration = (expectedIntervalNs * 10).toLong()
    now += stallDuration
    lagometer.update(stallDuration, minFrameDelay = 1, nowNs = now)

    println(
      "DEBUG: ByteValue=$byteValue, Interval=$expectedIntervalNs, Stall=$stallDuration, Lag=${lagometer.lag}"
    )

    assertTrue(lagometer.lag > 0.milliseconds, "Should report lag on spike. Got ${lagometer.lag}")
    // Roughly 7 intervals (Stall(10) - Buffer(2) - CurrentFrame(1))
    val expectedLagMs = (expectedIntervalNs * 7 / 1_000_000).toLong()
    val actualLagMs = lagometer.lag.inWholeMilliseconds

    // Allow small epsilon
    assertTrue(
      Math.abs(actualLagMs - expectedLagMs) < 10,
      "Expected ~$expectedLagMs ms lag, got $actualLagMs ms",
    )
  }

  private fun checkLagAttribution(p1FrameDelay: Int, p2FrameDelay: Int) {
    val frameDurationNs = 16.milliseconds.inWholeNanoseconds
    // Use high resolution for test
    val gameLagometer = Lagometer(frameDurationNs, historyResolution = 1.milliseconds)
    val p1Lagometer = Lagometer(frameDurationNs, historyResolution = 1.milliseconds)
    val p2Lagometer = Lagometer(frameDurationNs, historyResolution = 1.milliseconds)

    // Initialize baselines
    gameLagometer.update(0, min(p1FrameDelay, p2FrameDelay), 0)
    p1Lagometer.update(0, p1FrameDelay, 0)
    p2Lagometer.update(0, p2FrameDelay, 0)

    var lastFanout = 0L
    var random = Random(12345) // Seed for deterministic results

    for (i in 1..100) {
      val expectedNextFanout = lastFanout + frameDurationNs

      // Simulate random arrival times
      // Mostly on time, occasional spikes
      val p1Jitter = (random.nextGaussian() * frameDurationNs * 0.5).toLong()
      val p2Jitter = (random.nextGaussian() * frameDurationNs * 0.5).toLong()

      // Some pure stalls
      val p1Stall =
        if (random.nextFloat() < 0.1) (frameDurationNs * random.nextInt(5)).toLong() else 0L
      val p2Stall =
        if (random.nextFloat() < 0.1) (frameDurationNs * random.nextInt(5)).toLong() else 0L

      var arrivalP1 = expectedNextFanout + p1Jitter + p1Stall
      var arrivalP2 = expectedNextFanout + p2Jitter + p2Stall

      // Sanity check: can't arrive before previous processing
      if (arrivalP1 < lastFanout) arrivalP1 = lastFanout + 1
      if (arrivalP2 < lastFanout) arrivalP2 = lastFanout + 1

      // Game steps when all data is present (or timeout, but here strictly lockstep)
      // Lockstep: wait for slowest, but min step is frameDuration
      val actualFanout = max(max(arrivalP1, arrivalP2), expectedNextFanout)

      val gameDelta = actualFanout - lastFanout
      val p1Delta = arrivalP1 - lastFanout
      val p2Delta = arrivalP2 - lastFanout

      val minFrameDelay = min(p1FrameDelay, p2FrameDelay)

      gameLagometer.update(gameDelta, minFrameDelay, actualFanout)
      p1Lagometer.update(p1Delta, p1FrameDelay, actualFanout)
      p2Lagometer.update(p2Delta, p2FrameDelay, actualFanout)

      lastFanout = actualFanout
    }

    val sumPlayerLag = p1Lagometer.lag + p2Lagometer.lag
    val gameLag = gameLagometer.lag

    // Allow slight epsilon due to floating point conversions inside Lagometer/Duration if any,
    // but we use Longs mostly. Use logic assertion.
    println("GameLag: $gameLag, P1: ${p1Lagometer.lag}, P2: ${p2Lagometer.lag}, Sum: $sumPlayerLag")

    assertTrue(
      sumPlayerLag <= gameLag,
      "Sum of player lag ($sumPlayerLag) should not exceed game lag ($gameLag)",
    )
  }
}
