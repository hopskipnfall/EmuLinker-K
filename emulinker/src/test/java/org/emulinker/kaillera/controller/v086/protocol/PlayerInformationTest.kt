package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Ignore
import org.junit.Test

@Ignore
class PlayerInformationTest {

  @Test
  fun bodyLength() {
    assertThat(PLAYER_INFORMATION.bodyBytes).isEqualTo(2)
  }

  @Test
  fun deserializeBody() {
    assertThat(PlayerInformation.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(PLAYER_INFORMATION))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    PLAYER_INFORMATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(PLAYER_INFORMATION.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00,16"

    private val PLAYER_INFORMATION =
      PlayerInformation(
        MESSAGE_NUMBER,
        players =
          listOf(
            PlayerInformation.Player(
              username = "nue",
              ping = 100L,
              userId = 13,
              connectionType = ConnectionType.LAN
            ),
            PlayerInformation.Player(
              username = "nue1",
              ping = 100L,
              userId = 14,
              connectionType = ConnectionType.LAN
            ),
            PlayerInformation.Player(
              username = "nue2",
              ping = 100L,
              userId = 18,
              connectionType = ConnectionType.AVERAGE
            ),
            PlayerInformation.Player(
              username = "nue3",
              ping = 100L,
              userId = 200,
              connectionType = ConnectionType.LAN
            ),
            PlayerInformation.Player(
              username = "nue4",
              ping = 100L,
              userId = 12,
              connectionType = ConnectionType.LAN
            ),
            PlayerInformation.Player(
              username = "nue5",
              ping = 100L,
              userId = 8,
              connectionType = ConnectionType.BAD
            ),
            PlayerInformation.Player(
              username = "nue6",
              ping = 100L,
              userId = 3,
              connectionType = ConnectionType.BAD
            ),
          )
      )
  }
}
