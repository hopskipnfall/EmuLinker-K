package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Ignore
import org.junit.Test

class GameDataTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(GAME_DATA.bodyBytes).isEqualTo(8)
  }

  @Test
  @Ignore // Fails!
  fun byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES))
    assertThat(GameData.GameDataSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_DATA)
    assertThat(packet.endOfInput).isTrue()
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
    val buffer = ByteBuffer.allocateDirect(4096)
    GAME_DATA.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_DATA.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00, 00, 05, 01, 02, 03, 04, 05"

    private val GAME_DATA =
      GameData(messageNumber = MESSAGE_NUMBER, gameData = byteArrayOf(1, 2, 3, 4, 5))
  }
}
