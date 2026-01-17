package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Best-effort scheduler for low-priority tasks on a single thread.
 *
 * Scheduled tasks are wrapped in a try/catch to ensure that exceptions do not suppress subsequent
 * executions of repeating tasks (a behavior of [ScheduledExecutorService]).
 */
class TaskScheduler {
  private val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(/* target= */ runnable, /* name= */ "TaskScheduler").apply { isDaemon = true }
    }

  fun schedule(delay: Duration = 0.seconds, action: () -> Unit): ScheduledFuture<*> =
    executor.schedule(
      {
        try {
          action()
        } catch (e: Exception) {
          logger.atSevere().withCause(e).log("Exception in scheduled task!")
        }
      },
      /* delay= */ delay.inWholeMilliseconds,
      TimeUnit.MILLISECONDS,
    )

  fun scheduleRepeating(
    period: Duration,
    initialDelay: Duration = 0.seconds,
    taskName: String,
    action: () -> Unit,
  ): ScheduledFuture<*> =
    executor.scheduleAtFixedRate(
      {
        try {
          logger.atFine().log("Starting scheduled task: %s", taskName)
          action()
        } catch (e: Exception) {
          logger.atSevere().withCause(e).log("Exception in scheduled task!")
        } finally {
          logger.atFine().log("Completed scheduled task: %s", taskName)
        }
      },
      /* initialDelay= */ initialDelay.inWholeMilliseconds,
      /* period= */ period.inWholeMilliseconds,
      TimeUnit.MILLISECONDS,
    )

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
