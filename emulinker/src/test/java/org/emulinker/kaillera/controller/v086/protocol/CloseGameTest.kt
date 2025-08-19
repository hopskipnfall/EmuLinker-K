package org.emulinker.kaillera.controller.v086.protocol

// ProtocolBaseTest()
class CloseGameTest : V086MessageTest<CloseGame>() {
  override val message = CloseGame(messageNumber = 42, gameId = 10, val1 = 999)
  override val byteString = "00, 0A, 00, E7, 03"
  override val serializer = CloseGame.CloseGameSerializer
}
