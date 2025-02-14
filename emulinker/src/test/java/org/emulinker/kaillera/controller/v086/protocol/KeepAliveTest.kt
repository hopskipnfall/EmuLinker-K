package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
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
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(KeepAlive.KeepAliveSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(KEEP_ALIVE)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(KeepAlive.KeepAliveSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(KEEP_ALIVE)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
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
