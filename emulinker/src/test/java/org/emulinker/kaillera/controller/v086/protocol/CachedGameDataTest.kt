package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class CachedGameDataTest {

  @Test
  fun cachedGameData_bodyLength() {
    assertThat(CACHED_GAME_DATA.bodyBytes).isEqualTo(2)
  }

  @Test
  fun cachedGameData_deserializeBody() {
    assertThat(CachedGameData.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(CACHED_GAME_DATA))
  }

  @Test
  fun cachedGameData_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CACHED_GAME_DATA.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CACHED_GAME_DATA.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val CACHE_KEY = 22

    private const val BODY_BYTES = "00,16"

    private val CACHED_GAME_DATA = CachedGameData(MESSAGE_NUMBER, CACHE_KEY)
  }
}
