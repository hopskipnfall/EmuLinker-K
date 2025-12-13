package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Best-effort scheduler for low-priority tasks on a single thread.
 *
 * Scheduled tasks are wrapped in a try/catch to avoid unexpected exceptions from unintentionally
 * canceling the [Timer] instance being wrapped.
 */
class TaskScheduler {
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
    taskName: String,
    action: () -> Unit,
  ): TimerTask =
    timer.schedule(delay = initialDelay.inWholeMilliseconds, period = period.inWholeMilliseconds) {
      // Wrap the action in a try/catch to prevent exceptions from killing the timer.
      try {
        logger.atFine().log("Starting scheduled task: %s", taskName)
        action()
      } catch (e: InterruptedException) {
        // The server was probably told to shut down.
        throw e
      } catch (e: Exception) {
        logger.atSevere().withCause(e).log("Exception in scheduled task! Discarding.")
      } catch (e: Throwable) {
        // Some throwables such as IOException do not actually extend Exception and could break
        // execution of the task
        // scheduler. Allow it to break the scheduler, but log that it happened.
        logger.atSevere().withCause(e).log("Throwable in scheduled task. Rethrowing!")
        throw e
      } finally {
        logger.atFine().log("Completed scheduled task: %s", taskName)
      }
    }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
