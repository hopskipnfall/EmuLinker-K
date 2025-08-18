package org.emulinker.kaillera.controller.v086.protocol

class GameChatRequestTest : V086MessageTest<GameChat>() {
  override val message = GameChatRequest(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  override val byteString = "00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
  override val serializer = GameChat.GameChatSerializer
}

class GameChatNotificationTest : V086MessageTest<GameChat>() {
  override val message =
    GameChatNotification(
      messageNumber = MESSAGE_NUMBER,
      username = "nue",
      message = "Hello, world!",
    )
  override val byteString = "6E, 75, 65, 00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
  override val serializer = GameChat.GameChatSerializer
}
