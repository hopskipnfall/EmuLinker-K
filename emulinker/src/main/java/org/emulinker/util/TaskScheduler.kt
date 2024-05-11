package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Best-effort scheduler for low-priority tasks on a single thread.
 *
 * Scheduled tasks are wrapped in a try/catch to avoid unexpected exceptions from unintentionally
 * canceling the [Timer] instance being wrapped.
 */
@Singleton
class TaskScheduler @Inject constructor() {
  private var timer = Timer(/* isDaemon= */ true)

  fun schedule(delay: Duration = 0.seconds, action: () -> Unit): TimerTask =
    timer.schedule(delay = delay.inWholeMilliseconds) {
      // Wrap the action in a try/catch to prevent exceptions from killing the timer.
      try {
        action()
      } catch (e: Exception) {
        logger.atSevere().withCause(e).log("Exception in scheduled task!")
      }
    }

  fun scheduleRepeating(
    period: Duration,
    initialDelay: Duration = 0.seconds,
    action: () -> Unit
  ): TimerTask =
    timer.schedule(
      delay = initialDelay.inWholeMilliseconds,
      period = period.inWholeMilliseconds,
    ) {
      // Wrap the action in a try/catch to prevent exceptions from killing the timer.
      try {
        action()
      } catch (e: Exception) {
        logger.atSevere().withCause(e).log("Exception in scheduled task!")
      }
    }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
