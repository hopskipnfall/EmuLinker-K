package org.emulinker.kaillera.controller.v086.protocol

class QuitGameRequestTest : V086MessageTest<QuitGame>() {
  override val message = QuitGameRequest(messageNumber = MESSAGE_NUMBER)
  override val byteString = "00, FF, FF"
  override val serializer = QuitGame.QuitGameSerializer
}

class QuitGameNotificationTest : V086MessageTest<QuitGame>() {
  override val message =
    QuitGameNotification(messageNumber = MESSAGE_NUMBER, username = "nue", userId = 13)
  override val byteString = "6E, 75, 65, 00, 0D, 00"
  override val serializer = QuitGame.QuitGameSerializer
}
