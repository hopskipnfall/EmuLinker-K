package org.emulinker.kaillera.controller.v086.protocol

class ConnectionRejectedTest : V086MessageTest<ConnectionRejected>() {
  override val message =
    ConnectionRejected(
      messageNumber = 42,
      username = "nue",
      userId = 100,
      message = "This is a message!",
    )
  override val byteString =
    "6E, 75, 65, 00, 64, 00, 54, 68, 69, 73, 20, 69, 73, 20, 61, 20, 6D, 65, 73, 73, 61, 67, 65, 21, 00"
  override val serializer = ConnectionRejected.ConnectionRejectedSerializer
}
