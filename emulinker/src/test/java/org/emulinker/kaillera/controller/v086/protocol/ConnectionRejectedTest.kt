package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class ConnectionRejectedTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(CONNECTION_REJECTED.bodyBytes).isEqualTo(25)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(
        ConnectionRejected.ConnectionRejectedSerializer.read(packet, MESSAGE_NUMBER).getOrThrow()
      )
      .isEqualTo(CONNECTION_REJECTED)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    assertThat(
        ConnectionRejected.ConnectionRejectedSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow()
      )
      .isEqualTo(CONNECTION_REJECTED)
    assertThat(buffer.hasRemaining()).isFalse()
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
    private const val BODY_BYTES =
      "6E, 75, 65, 00, 00, 64, 54, 68, 69, 73, 20, 69, 73, 20, 61, 20, 6D, 65, 73, 73, 61, 67, 65, 21, 00"

    private val CONNECTION_REJECTED =
      ConnectionRejected(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        userId = 100,
        message = "This is a message!"
      )
  }
}
