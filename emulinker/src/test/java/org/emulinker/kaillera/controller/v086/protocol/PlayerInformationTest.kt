package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Test

class PlayerInformationTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(PLAYER_INFORMATION.bodyBytes).isEqualTo(88)
  }

  @Test
  fun deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    assertThat(
        PlayerInformation.PlayerInformationSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow()
      )
      .isEqualTo(PLAYER_INFORMATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun serializeBody() {
    val buffer = allocateByteBuffer()
    PLAYER_INFORMATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(PLAYER_INFORMATION.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES =
      "00, 07, 00, 00, 00, 6E, 75, 65, 00, 64, 00, 00, 00, 0D, 00, 01, 6E, 75, 65, 31, 00, 64, 00, 00, 00, 0E, 00, 01, 6E, 75, 65, 32, 00, 64, 00, 00, 00, 12, 00, 04, 6E, 75, 65, 33, 00, 64, 00, 00, 00, C8, 00, 01, 6E, 75, 65, 34, 00, 64, 00, 00, 00, 0C, 00, 01, 6E, 75, 65, 35, 00, 64, 00, 00, 00, 08, 00, 06, 6E, 75, 65, 36, 00, 64, 00, 00, 00, 03, 00, 06"

    private val PLAYER_INFORMATION =
      PlayerInformation(
        MESSAGE_NUMBER,
        players =
          listOf(
            PlayerInformation.Player(
              username = "nue",
              ping = 100L.milliseconds,
              userId = 13,
              connectionType = ConnectionType.LAN,
            ),
            PlayerInformation.Player(
              username = "nue1",
              ping = 100L.milliseconds,
              userId = 14,
              connectionType = ConnectionType.LAN,
            ),
            PlayerInformation.Player(
              username = "nue2",
              ping = 100L.milliseconds,
              userId = 18,
              connectionType = ConnectionType.AVERAGE,
            ),
            PlayerInformation.Player(
              username = "nue3",
              ping = 100L.milliseconds,
              userId = 200,
              connectionType = ConnectionType.LAN,
            ),
            PlayerInformation.Player(
              username = "nue4",
              ping = 100L.milliseconds,
              userId = 12,
              connectionType = ConnectionType.LAN,
            ),
            PlayerInformation.Player(
              username = "nue5",
              ping = 100L.milliseconds,
              userId = 8,
              connectionType = ConnectionType.BAD,
            ),
            PlayerInformation.Player(
              username = "nue6",
              ping = 100.milliseconds,
              userId = 3,
              connectionType = ConnectionType.BAD,
            ),
          ),
      )
  }
}
