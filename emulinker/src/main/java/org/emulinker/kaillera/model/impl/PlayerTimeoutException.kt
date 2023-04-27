package org.emulinker.kaillera.model.impl

import java.lang.Exception
import org.emulinker.kaillera.model.KailleraUser

data class PlayerTimeoutException(
  val playerNumber: Int,
  var timeoutNumber: Int = -1,
  val player: KailleraUser? = null
) : Exception() {
  // TODO(nue): Figure out if it's safe to get rid of this equals method.
  override fun equals(other: Any?): Boolean {
    if (other != null && other is PlayerTimeoutException) {
      if (other.playerNumber == playerNumber && other.timeoutNumber == timeoutNumber) return true
    }
    return false
  }
}
