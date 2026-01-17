package org.emulinker.kaillera.model.impl

import io.netty.buffer.ByteBuf
import org.emulinker.kaillera.model.KailleraUser

interface AutoFireDetector {
  var sensitivity: Int

  fun start(numPlayers: Int)

  fun addPlayer(user: KailleraUser, playerNumber: Int)

  fun addData(playerNumber: Int, data: ByteBuf, bytesPerAction: Int)

  fun stop(playerNumber: Int)

  fun stop()
}
