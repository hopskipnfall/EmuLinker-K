package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class GameKickTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(GAME_KICK.bodyBytes).isEqualTo(3)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        GameKick.GameKickSerializer.read(
          V086Utils.hexStringToByteBuffer(BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(GAME_KICK))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    GAME_KICK.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_KICK.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 00, 0D"

    private val GAME_KICK = GameKick(MESSAGE_NUMBER, userId = 13)
  }
}
