package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Ignore
import org.junit.Test

@Ignore
class ConnectionRejectedTest {

  @Test
  fun bodyLength() {
    assertThat(CONNECTION_REJECTED.bodyBytes).isEqualTo(2)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        ConnectionRejected.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES))
      )
      .isEqualTo(MessageParseResult.Success(CONNECTION_REJECTED))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CONNECTION_REJECTED.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CONNECTION_REJECTED.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00,16"

    private val CONNECTION_REJECTED =
      ConnectionRejected(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        userId = 100,
        message = "This is a message!"
      )
  }
}
