package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

class TaskSchedulerErrorTest {
  @Test
  fun repeatingTaskKeepsRunningAfterAnError() {
    val scheduler = TaskScheduler()
    val runs = AtomicInteger()
    val done = CountDownLatch(3)

    val future =
      scheduler.scheduleRepeating(period = 10.milliseconds, taskName = "test") {
        runs.incrementAndGet()
        done.countDown()
        throw NoClassDefFoundError("simulated")
      }

    // An Error (not an Exception) used to cancel the task after the first run.
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(runs.get()).isAtLeast(3)
    future.cancel(false)
  }
}
