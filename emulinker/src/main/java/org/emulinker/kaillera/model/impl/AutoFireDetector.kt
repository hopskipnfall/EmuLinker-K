package org.emulinker.kaillera.model.impl

import org.emulinker.kaillera.model.KailleraUser
import io.netty.buffer.ByteBuf

interface AutoFireDetector {
  var sensitivity: Int

  fun start(numPlayers: Int)

  fun addPlayer(user: KailleraUser, playerNumber: Int)

  fun addData(playerNumber: Int, data: ByteBuf, bytesPerAction: Int)

  fun stop(playerNumber: Int)

  fun stop()
}
