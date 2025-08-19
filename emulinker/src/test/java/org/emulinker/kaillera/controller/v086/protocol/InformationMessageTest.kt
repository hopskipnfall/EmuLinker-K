package org.emulinker.kaillera.controller.v086.protocol

class InformationMessageTest : V086MessageTest<InformationMessage>() {
  override val message =
    InformationMessage(MESSAGE_NUMBER, source = "This is a source", message = "Hello, world!")
  override val byteString =
    "54, 68, 69, 73, 20, 69, 73, 20, 61, 20, 73, 6F, 75, 72, 63, 65, 00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
  override val serializer = InformationMessage.InformationMessageSerializer
}
