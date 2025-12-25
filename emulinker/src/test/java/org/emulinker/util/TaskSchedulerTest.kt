package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

class TaskSchedulerTest {

  @Test
  fun schedule_runsTask() {
    val scheduler = TaskScheduler()
    val latch = CountDownLatch(1)

    scheduler.schedule(10.milliseconds) { latch.countDown() }

    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun scheduleRepeating_runsRepeatedly() {
    val scheduler = TaskScheduler()
    val latch = CountDownLatch(3)

    val future =
      scheduler.scheduleRepeating(
        period = 10.milliseconds,
        initialDelay = 0.milliseconds,
        taskName = "testTask",
      ) {
        latch.countDown()
      }

    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
    future.cancel(/* mayInterruptIfRunning= */ false)
  }

  @Test
  fun scheduleRepeating_continuesAfterException() {
    val scheduler = TaskScheduler()
    val latch = CountDownLatch(3)
    var exceptionThrown = false

    val future =
      scheduler.scheduleRepeating(
        period = 10.milliseconds,
        initialDelay = 0.milliseconds,
        taskName = "exceptionTask",
      ) {
        if (!exceptionThrown) {
          exceptionThrown = true
          throw RuntimeException("Boom!")
        } else {
          latch.countDown()
        }
      }

    // If the scheduler didn't catch the exception, the task would stop and the latch would never
    // reach 0 (or at least 2 remaining)
    // Since we count down 3 times *after* the exception, this proves it's still running.
    val success = latch.await(1, TimeUnit.SECONDS)
    future.cancel(/* mayInterruptIfRunning= */ false)

    assertThat(exceptionThrown).isTrue()
    assertThat(success).isTrue()
  }
}
