package org.emulinker.kaillera.controller.v086.protocol

class PlayerDropRequestTest : V086MessageTest<PlayerDrop>() {
  override val message = PlayerDropRequest(messageNumber = MESSAGE_NUMBER)
  override val byteString = "00, 00"
  override val serializer = PlayerDrop.PlayerDropSerializer
}

class PlayerDropNotificationTest : V086MessageTest<PlayerDrop>() {
  override val message =
    PlayerDropNotification(messageNumber = MESSAGE_NUMBER, username = "nue", playerNumber = 100)
  override val byteString = "6E, 75, 65, 00, 64"
  override val serializer = PlayerDrop.PlayerDropSerializer
}
