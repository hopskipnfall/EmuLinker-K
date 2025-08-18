package org.emulinker.kaillera.controller.v086.protocol

class QuitRequestTest : V086MessageTest<Quit>() {
  override val message = QuitRequest(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  override val byteString = "00, FF, FF, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
  override val serializer = Quit.QuitSerializer
}

class QuitNotificationTest : V086MessageTest<Quit>() {
  override val message =
    QuitNotification(
      messageNumber = MESSAGE_NUMBER,
      username = "nue",
      userId = 13,
      message = "Hello, world!",
    )
  override val byteString =
    "6E, 75, 65, 00, 0D, 00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
  override val serializer = Quit.QuitSerializer
}
