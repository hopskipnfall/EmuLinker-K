package org.emulinker.kaillera.controller.v086.protocol

class StartGameRequestTest : V086MessageTest<StartGame>() {
  override val message = StartGameRequest(messageNumber = MESSAGE_NUMBER)
  override val byteString = "00, FF, FF, FF, FF"
  override val serializer = StartGame.StartGameSerializer
}

class StartGameNotificationTest : V086MessageTest<StartGame>() {
  override val message =
    StartGameNotification(
      messageNumber = MESSAGE_NUMBER,
      numPlayers = 4,
      playerNumber = 42,
      val1 = 2000,
    )
  override val byteString = "00, D0, 07, 2A, 04"
  override val serializer = StartGame.StartGameSerializer
}
