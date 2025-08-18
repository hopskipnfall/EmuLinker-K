package org.emulinker.kaillera.controller.v086.protocol

class KeepAliveTest : V086MessageTest<KeepAlive>() {
  override val message = KeepAlive(MESSAGE_NUMBER, value = 12)
  override val byteString = "0C"
  override val serializer = KeepAlive.KeepAliveSerializer
}
