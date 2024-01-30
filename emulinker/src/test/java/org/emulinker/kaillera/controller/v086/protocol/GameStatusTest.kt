package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.GameStatus.SYNCHRONIZING
import org.junit.Test

class GameStatusTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(GAME_STATUS.bodyBytes).isEqualTo(8)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    assertThat(
        GameStatus.GameStatusSerializer.read(
            ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES)),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(GAME_STATUS)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        GameStatus.GameStatusSerializer.read(
            V086Utils.hexStringToByteBuffer(BODY_BYTES),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(GAME_STATUS)
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    GAME_STATUS.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_STATUS.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 00, 0D, 09, 29, 01, 04, 04"

    private val GAME_STATUS =
      GameStatus(
        messageNumber = MESSAGE_NUMBER,
        gameId = 13,
        val1 = 2345,
        gameStatus = SYNCHRONIZING,
        numPlayers = 4,
        maxPlayers = 4
      )
  }
}
