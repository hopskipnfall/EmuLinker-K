package org.emulinker.kaillera.controller.connectcontroller.protocol

import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.junit.Test

class RequestPrivateKailleraPortResponseTest : ProtocolBaseTest() {

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
    val MESSAGE = RequestPrivateKailleraPortResponse(port = 424242)

    const val BODY_BYTES = "48,45,4C,4C,4F,44,30,30,44,34,32,34,32,34,32,00"
  }
}
