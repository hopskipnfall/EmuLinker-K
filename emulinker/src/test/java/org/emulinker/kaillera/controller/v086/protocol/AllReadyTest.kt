package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class AllReadyTest : ProtocolBaseTest() {

  @Test
  fun allReady_bodyLength() {
    assertThat(ALL_READY.bodyBytes).isEqualTo(1)
  }

  @Test
  fun allReady_deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(AllReady.AllReadySerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(ALL_READY)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
  }

  @Test
  fun allReady_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(AllReady.AllReadySerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(ALL_READY)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun allReady_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    ALL_READY.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(ALL_READY.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00"

    private val ALL_READY = AllReady(MESSAGE_NUMBER)
  }
}
