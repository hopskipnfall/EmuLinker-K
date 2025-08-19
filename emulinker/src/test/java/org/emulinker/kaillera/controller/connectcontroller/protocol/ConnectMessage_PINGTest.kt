package org.emulinker.kaillera.controller.connectcontroller.protocol

import org.emulinker.kaillera.controller.v086.protocol.ConnectMessageTest

class ConnectMessagePingTest() : ConnectMessageTest<ConnectMessage_PING>() {
  override val message = ConnectMessage_PING
  override val byteString = "50,49,4E,47,00"
}
