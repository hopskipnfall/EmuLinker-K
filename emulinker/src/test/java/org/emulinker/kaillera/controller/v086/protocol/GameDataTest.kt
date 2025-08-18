package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.util.VariableSizeByteArray
import org.junit.Test

class GameDataTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(GAME_DATA.bodyBytes).isEqualTo(8)
  }

  @Test
  fun deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    assertThat(GameData.GameDataSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_DATA)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun serializeBody() {
    val buffer = allocateByteBuffer()
    GAME_DATA.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_DATA.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 05, 00, 02, 03, 04, 05, 06"
    private val GAME_DATA =
      GameData(
        messageNumber = MESSAGE_NUMBER,
        gameData = VariableSizeByteArray(byteArrayOf(2, 3, 4, 5, 6)),
      )
  }
}
