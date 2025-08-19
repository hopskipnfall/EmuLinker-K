package org.emulinker.kaillera.controller.connectcontroller.protocol

import org.emulinker.kaillera.controller.v086.protocol.ConnectMessageTest

class ConnectMessageTooTest() : ConnectMessageTest<ConnectMessage_ServerFull>() {
  override val message = ConnectMessage_ServerFull
  override val byteString = "54,4F,4F,00"
}
