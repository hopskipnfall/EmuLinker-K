package org.emulinker.util

import kotlin.coroutines.CoroutineContext

// TODO(nue): Get rid of this.
interface Executable {
  val threadIsActive: Boolean

  suspend fun stop()

  suspend fun run(globalContext: CoroutineContext)
}
