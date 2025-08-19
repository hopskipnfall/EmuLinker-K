package org.emulinker.kaillera.controller.connectcontroller.protocol

import org.emulinker.kaillera.controller.v086.protocol.ConnectMessageTest

class RequestPrivateKailleraPortRequestTest() :
  ConnectMessageTest<RequestPrivateKailleraPortRequest>() {
  override val message = RequestPrivateKailleraPortRequest(protocol = "0.86")
  override val byteString = "48,45,4C,4C,4F,30,2E,38,36,00"
}
