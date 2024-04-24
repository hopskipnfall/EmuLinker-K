package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class InformationMessageTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(INFORMATION_MESSAGE.bodyBytes).isEqualTo(31)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(
        InformationMessage.InformationMessageSerializer.read(packet, MESSAGE_NUMBER).getOrThrow()
      )
      .isEqualTo(INFORMATION_MESSAGE)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    assertThat(
        InformationMessage.InformationMessageSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow()
      )
      .isEqualTo(INFORMATION_MESSAGE)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    INFORMATION_MESSAGE.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(INFORMATION_MESSAGE.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES =
      "54, 68, 69, 73, 20, 69, 73, 20, 61, 20, 73, 6F, 75, 72, 63, 65, 00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"

    private val INFORMATION_MESSAGE =
      InformationMessage(MESSAGE_NUMBER, source = "This is a source", message = "Hello, world!")
  }
}
