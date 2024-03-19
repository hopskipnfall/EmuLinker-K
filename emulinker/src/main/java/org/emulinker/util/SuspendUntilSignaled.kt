package org.emulinker.util

import java.util.concurrent.locks.Condition
import javax.inject.Inject
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Intended as a coroutines-friendly implementation of [Condition]. */
@Deprecated("Avoid coroutines for overhead", level = DeprecationLevel.ERROR)
class SuspendUntilSignaled @Inject constructor() {
  private val jobs = mutableListOf<CompletableJob>()
  private val mutex = Mutex()

  /** Resumes execution for all coroutines suspended by calling [suspendUntilSignaled]. */
  suspend fun signalAll() =
    mutex.withLock {
      jobs.forEach { it.complete() }
      jobs.clear()
    }

  /** Suspends execution of the current coroutine until [signalAll] is invoked. */
  suspend fun suspendUntilSignaled() {
    val job = Job()
    mutex.withLock { jobs.add(job) }
    job.join()
  }
}
