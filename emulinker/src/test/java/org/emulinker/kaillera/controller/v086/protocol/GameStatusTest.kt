package org.emulinker.kaillera.controller.v086.protocol

import org.emulinker.kaillera.model.GameStatus.SYNCHRONIZING

class GameStatusTest : V086MessageTest<GameStatus>() {
  override val message =
    GameStatus(
      messageNumber = MESSAGE_NUMBER,
      gameId = 13,
      val1 = 2345,
      gameStatus = SYNCHRONIZING,
      numPlayers = 4,
      maxPlayers = 4,
    )
  override val byteString = "00, 0D, 00, 29, 09, 01, 04, 04"
  override val serializer = GameStatus.GameStatusSerializer
}
