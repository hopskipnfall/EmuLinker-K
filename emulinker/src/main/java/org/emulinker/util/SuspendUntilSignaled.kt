package org.emulinker.util

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel

// TODO(nue): Make this not terrible.
class SuspendUntilSignaled @Inject constructor() {
  private val signalThing = Channel<Int>(1_000)

  val numListeners = AtomicInteger(/* initialValue= */ 0)

  suspend fun signalAll() {
    if (numListeners.get() > 0) {
      signalThing.trySend(1)
    }
  }

  suspend fun suspendUntilSignaled() {
    numListeners.incrementAndGet()
    signalThing.receive()
  }
}
