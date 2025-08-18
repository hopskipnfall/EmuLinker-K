package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class KeepAliveTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(KEEP_ALIVE.bodyBytes).isEqualTo(1)
  }

  @Test
  fun deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    assertThat(KeepAlive.KeepAliveSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(KEEP_ALIVE)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun serializeBody() {
    val buffer = allocateByteBuffer()
    KEEP_ALIVE.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(KEEP_ALIVE.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "0C"

    private val KEEP_ALIVE = KeepAlive(MESSAGE_NUMBER, value = 12)
  }
}
