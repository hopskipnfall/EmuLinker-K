package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class AllReadyTest {

  @Test
  fun allReady_bodyLength() {
    assertThat(ALL_READY.bodyBytes).isEqualTo(1)
  }

  @Test
  fun allReady_deserializeBody() {
    assertThat(AllReady.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(ALL_READY))
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
