package org.emulinker.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class TaskSchedulerErrorTest :
  FunSpec({
    test("repeatingTaskKeepsRunningAfterAnError") {
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
      done.await(5, TimeUnit.SECONDS).shouldBeTrue()
      runs.get() shouldBeGreaterThanOrEqual 3
      future.cancel(false)
    }
  })
