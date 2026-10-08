package org.emulinker.kaillera.controller.v086.protocol

class GameKickTest : V086MessageTest<GameKick>() {
  override val message = GameKick(MESSAGE_NUMBER, userId = 13)
  override val byteString = "00, 0D, 00"
  override val serializer = GameKick.GameKickSerializer
}

/** IDs >= 32768 must round-trip; they were previously rejected by a signed 16-bit read. */
class GameKickHighIdTest : V086MessageTest<GameKick>() {
  override val message = GameKick(MESSAGE_NUMBER, userId = 40000)
  override val byteString = "00, 40, 9C"
  override val serializer = GameKick.GameKickSerializer
}
