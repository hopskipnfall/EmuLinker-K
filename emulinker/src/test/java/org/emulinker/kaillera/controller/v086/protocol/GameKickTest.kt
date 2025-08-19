package org.emulinker.kaillera.controller.v086.protocol

class GameKickTest : V086MessageTest<GameKick>() {
  override val message = GameKick(MESSAGE_NUMBER, userId = 13)
  override val byteString = "00, 0D, 00"
  override val serializer = GameKick.GameKickSerializer
}
