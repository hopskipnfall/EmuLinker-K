package org.emulinker.kaillera.model.impl

import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.VariableSizeByteArray

interface AutoFireDetector {
  var sensitivity: Int

  fun start(numPlayers: Int)

  fun addPlayer(user: KailleraUser, playerNumber: Int)

  fun addData(playerNumber: Int, data: VariableSizeByteArray, bytesPerAction: Int)

  fun stop(playerNumber: Int)

  fun stop()
}
