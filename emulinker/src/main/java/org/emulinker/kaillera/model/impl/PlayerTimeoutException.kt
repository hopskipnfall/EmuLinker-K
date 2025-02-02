package org.emulinker.kaillera.model.impl

import java.lang.Exception
import org.emulinker.kaillera.model.KailleraUser

data class PlayerTimeoutException(
  val playerNumber: Int,
  var timeoutNumber: Int = -1,
  val player: KailleraUser? = null,
) : Exception() {
  // TODO(nue): Figure out if it's safe to get rid of this equals method.
  override fun equals(o: Any?): Boolean {
    if (o != null && o is PlayerTimeoutException) {
      if (o.playerNumber == playerNumber && o.timeoutNumber == timeoutNumber) return true
    }
    return false
  }
}
