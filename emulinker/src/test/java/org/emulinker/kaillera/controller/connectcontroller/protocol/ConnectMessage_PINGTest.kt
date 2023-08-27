package org.emulinker.kaillera.controller.connectcontroller.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.junit.Test

class ConnectMessage_PINGTest : ProtocolBaseTest() {

  @Test
  fun serialize() {
    val buffer = ByteBuffer.allocateDirect(4096)
    MESSAGE.writeTo(buffer)

    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  @Test
  fun deserialize() {
    ConnectMessage.parse(V086Utils.hexStringToByteBuffer(BODY_BYTES))
  }

  private companion object {
    val MESSAGE = ConnectMessage_PING()

    const val BODY_BYTES = "50,49,4E,47,00"
  }
}
