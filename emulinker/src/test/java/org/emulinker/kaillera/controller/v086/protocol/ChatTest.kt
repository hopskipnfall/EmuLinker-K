package org.emulinker.kaillera.controller.v086.protocol

class ChatRequestTest : V086MessageTest<Chat>() {
  override val message = ChatRequest(messageNumber = 42, message = "Hello, world!")
  override val byteString = "00,48,65,6C,6C,6F,2C,20,77,6F,72,6C,64,21,00"
  override val serializer = Chat.ChatSerializer
}

class ChatNotificationTest : V086MessageTest<Chat>() {
  override val message =
    ChatNotification(messageNumber = 42, username = "nue", message = "Hello, world!")
  override val byteString = "6E,75,65,00,48,65,6C,6C,6F,2C,20,77,6F,72,6C,64,21,00"
  override val serializer = Chat.ChatSerializer
}
