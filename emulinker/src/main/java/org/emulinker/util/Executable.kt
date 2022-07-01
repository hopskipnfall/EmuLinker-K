package org.emulinker.util

interface Executable {
  val threadIsActive: Boolean

  suspend fun stop()

  suspend fun run()
}
