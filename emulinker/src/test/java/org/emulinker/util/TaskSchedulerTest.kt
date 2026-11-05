package org.emulinker.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class TaskSchedulerTest :
  FunSpec({
    test("schedule_runsTask") {
      val scheduler = TaskScheduler()
      val latch = CountDownLatch(1)

      scheduler.schedule(10.milliseconds) { latch.countDown() }

      latch.await(1, TimeUnit.SECONDS).shouldBeTrue()
    }

    test("scheduleRepeating_runsRepeatedly") {
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

      latch.await(1, TimeUnit.SECONDS).shouldBeTrue()
      future.cancel(/* mayInterruptIfRunning= */ false)
    }

    test("scheduleRepeating_continuesAfterException") {
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

      exceptionThrown.shouldBeTrue()
      success.shouldBeTrue()
    }
  })
