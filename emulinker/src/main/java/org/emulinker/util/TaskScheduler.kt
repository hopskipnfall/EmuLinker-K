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
 * [Timer] is not used directly, as it seems to get confused and throw exceptions saying it's
 * already canceled.
 */
@Singleton
class TaskScheduler @Inject constructor() {
  private var timer = Timer()

  fun schedule(delay: Duration = 0.seconds, action: TimerTask.() -> Unit): TimerTask =
    try {
      timer.schedule(delay = delay.inWholeMilliseconds, action)
    } catch (e: Exception) {
      logger
        .atWarning()
        .withCause(e)
        .log("Something went wrong scheduling task. Trying again on a new timer instance.")

      timer = Timer()

      timer.schedule(delay = delay.inWholeMilliseconds, action)
    }

  fun scheduleRepeating(
    period: Duration,
    initialDelay: Duration = 0.seconds,
    action: TimerTask.() -> Unit
  ): TimerTask =
    try {
      timer.schedule(
        delay = initialDelay.inWholeMilliseconds,
        period = period.inWholeMilliseconds,
        action
      )
    } catch (e: Exception) {
      logger
        .atWarning()
        .withCause(e)
        .log("Something went wrong scheduling task. Trying again on a new timer instance.")

      timer = Timer()

      timer.schedule(
        delay = initialDelay.inWholeMilliseconds,
        period = period.inWholeMilliseconds,
        action
      )
    }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
