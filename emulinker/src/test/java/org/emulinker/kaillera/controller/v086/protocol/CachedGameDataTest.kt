package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class CachedGameDataTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(CACHED_GAME_DATA.bodyBytes).isEqualTo(2)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        CachedGameData.CachedGameDataSerializer.read(
          V086Utils.hexStringToByteBuffer(BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(CACHED_GAME_DATA))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CACHED_GAME_DATA.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CACHED_GAME_DATA.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 0C"

    private val CACHED_GAME_DATA = CachedGameData(MESSAGE_NUMBER, key = 12)
  }
}
