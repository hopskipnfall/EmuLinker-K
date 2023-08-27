package org.emulinker.org.emulinker.util

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.emulinker.testing.LoggingRule
import org.emulinker.util.SuspendUntilSignaled
import org.junit.Rule
import org.junit.Test

class SuspendUntilSignaledTest {
  @get:Rule val logging = LoggingRule()

  private val target = SuspendUntilSignaled()

  @Test
  fun itWorks() = runBlocking {
    val job = async { target.suspendUntilSignaled() }

    delay(1.seconds)
    assertThat(job.isCompleted).isFalse()

    target.signalAll()
    delay(1.seconds)

    assertThat(job.isCompleted).isTrue()
  }
}
