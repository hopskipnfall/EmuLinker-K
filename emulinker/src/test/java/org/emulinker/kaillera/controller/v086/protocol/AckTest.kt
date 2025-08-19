package org.emulinker.kaillera.controller.v086.protocol

class ClientAckTest : V086MessageTest<ClientAck>() {
  override val message = ClientAck(MESSAGE_NUMBER)
  override val byteString = "00, 00, 00, 00, 00, 01, 00, 00, 00, 02, 00, 00, 00, 03, 00, 00, 00"
  override val serializer = Ack.ClientAckSerializer
}

class ServerAckTest : V086MessageTest<ServerAck>() {
  override val message = ServerAck(MESSAGE_NUMBER)
  override val byteString = "00, 00, 00, 00, 00, 01, 00, 00, 00, 02, 00, 00, 00, 03, 00, 00, 00"
  override val serializer = Ack.ServerAckSerializer
}
