package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class KeepAliveTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(KEEP_ALIVE.bodyBytes).isEqualTo(1)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    assertThat(
        KeepAlive.KeepAliveSerializer.read(
            ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES)),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(KEEP_ALIVE)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        KeepAlive.KeepAliveSerializer.read(
            V086Utils.hexStringToByteBuffer(BODY_BYTES),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(KEEP_ALIVE)
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
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
