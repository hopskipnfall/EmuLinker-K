package org.emulinker.kaillera.controller.connectcontroller.protocol

import org.emulinker.kaillera.controller.v086.protocol.ConnectMessageTest

class ConnectMessagePongTest() : ConnectMessageTest<ConnectMessage_PONG>() {
  override val message = ConnectMessage_PONG
  override val byteString = "50,4F,4E,47,00"
}
