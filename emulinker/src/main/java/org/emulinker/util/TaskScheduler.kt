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
  private var timer = Timer(/* isDaemon= */ true)

  fun schedule(delay: Duration = 0.seconds, action: () -> Unit): TimerTask =
    try {
      timer.schedule(delay = delay.inWholeMilliseconds) {
        // Wrap the action in a try/catch to prevent exceptions from killing the timer.
        try {
          action()
        } catch (e: Exception) {
          logger
            .atWarning()
            .withCause(e)
            .log("Caught an exception that would have killed the timer")
        }
      }
    } catch (e: Exception) {
      logger
        .atWarning()
        .withCause(e)
        .log("Something went wrong scheduling task. Trying again on a new timer instance.")

      timer = Timer()

      timer.schedule(delay = delay.inWholeMilliseconds) {
        // Wrap the action in a try/catch to prevent exceptions from killing the timer.
        try {
          action()
        } catch (e: Exception) {
          logger
            .atWarning()
            .withCause(e)
            .log("Caught an exception that would have killed the timer")
        }
      }
    }

  fun scheduleRepeating(
    period: Duration,
    initialDelay: Duration = 0.seconds,
    action: () -> Unit
  ): TimerTask =
    try {
      timer.schedule(
        delay = initialDelay.inWholeMilliseconds,
        period = period.inWholeMilliseconds,
      ) {
        // Wrap the action in a try/catch to prevent exceptions from killing the timer.
        try {
          action()
        } catch (e: Exception) {
          logger
            .atWarning()
            .withCause(e)
            .log("Caught an exception that would have killed the timer")
        }
      }
    } catch (e: Exception) {
      logger
        .atWarning()
        .withCause(e)
        .log("Something went wrong scheduling task. Trying again on a new timer instance.")

      timer = Timer()

      timer.schedule(
        delay = initialDelay.inWholeMilliseconds,
        period = period.inWholeMilliseconds
      ) {
        // Wrap the action in a try/catch to prevent exceptions from killing the timer.
        try {
          action()
        } catch (e: Exception) {
          logger
            .atWarning()
            .withCause(e)
            .log("Caught an exception that would have killed the timer")
        }
      }
    }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
