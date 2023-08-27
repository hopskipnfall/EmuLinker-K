package org.emulinker.util

import java.util.concurrent.locks.Condition
import javax.inject.Inject
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Intended as a coroutines-friendly implementation of [Condition]. */
class SuspendUntilSignaled @Inject constructor() {
  private val jobs = mutableListOf<CompletableJob>()

  private val mutex = Mutex()

  suspend fun signalAll() =
    mutex.withLock {
      jobs.forEach { it.complete() }
      jobs.clear()
    }

  suspend fun suspendUntilSignaled() {
    val job = Job()
    job.start()
    mutex.withLock { jobs.add(job) }

    job.join()
  }
}
