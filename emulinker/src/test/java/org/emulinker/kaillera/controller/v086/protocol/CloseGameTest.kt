package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class CloseGameTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(CLOSE_GAME.bodyBytes).isEqualTo(5)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        CloseGame.CloseGameSerializer.read(
          V086Utils.hexStringToByteBuffer(BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(CLOSE_GAME))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CLOSE_GAME.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CLOSE_GAME.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 00, 0A, 03, E7"

    private val CLOSE_GAME = CloseGame(messageNumber = MESSAGE_NUMBER, gameId = 10, val1 = 999)
  }
}
