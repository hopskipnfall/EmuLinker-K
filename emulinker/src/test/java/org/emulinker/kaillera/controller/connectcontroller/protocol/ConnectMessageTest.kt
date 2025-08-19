package org.emulinker.kaillera.controller.connectcontroller.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.junit.Test

class ConnectMessageTest : ProtocolBaseTest() {
  @Test
  fun byteBuffer_invalidCase_shouldResetPosition() {
    val buffer = V086Utils.hexStringToByteBuffer(UNKNOWN_BYTES)
    assertThat(buffer.position()).isEqualTo(0)

    val result = ConnectMessage.parse(buffer)

    assertThat(result.isFailure)
    assertThat(buffer.position()).isEqualTo(0)
  }

  @Test
  fun byteBuf_invalidCase_shouldResetPosition() {
    val byteBuf = Unpooled.buffer(4096)
    byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(UNKNOWN_BYTES))
    assertThat(byteBuf.readableBytes()).isEqualTo(2)

    val result = ConnectMessage.parse(byteBuf)

    assertThat(result.isFailure)
    assertThat(byteBuf.readableBytes()).isEqualTo(2)
  }

  private companion object {
    const val UNKNOWN_BYTES = "42, 42"
  }
}
