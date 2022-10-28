package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.UserStatus
import org.junit.Ignore
import org.junit.Test

@Ignore
class ServerStatusTest {

  @Test
  fun bodyLength() {
    assertThat(SERVER_STATUS.bodyBytes).isEqualTo(2)
  }

  @Test
  fun deserializeBody() {
    assertThat(ServerStatus.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(SERVER_STATUS))
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    SERVER_STATUS.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(SERVER_STATUS.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00,16"

    private val SERVER_STATUS =
      ServerStatus(
        MESSAGE_NUMBER,
        users =
          listOf(
            ServerStatus.User(
              username = "nue",
              ping = 100L,
              userId = 13,
              connectionType = ConnectionType.LAN,
              status = UserStatus.CONNECTING
            ),
            ServerStatus.User(
              username = "nue1",
              ping = 100L,
              userId = 14,
              connectionType = ConnectionType.LAN,
              status = UserStatus.IDLE
            ),
            ServerStatus.User(
              username = "nue2",
              ping = 100L,
              userId = 18,
              connectionType = ConnectionType.AVERAGE,
              status = UserStatus.PLAYING
            ),
            ServerStatus.User(
              username = "nue3",
              ping = 100L,
              userId = 200,
              connectionType = ConnectionType.LAN,
              status = UserStatus.CONNECTING
            ),
            ServerStatus.User(
              username = "nue4",
              ping = 100L,
              userId = 12,
              connectionType = ConnectionType.LAN,
              status = UserStatus.PLAYING
            ),
            ServerStatus.User(
              username = "nue5",
              ping = 100L,
              userId = 8,
              connectionType = ConnectionType.BAD,
              status = UserStatus.CONNECTING
            ),
            ServerStatus.User(
              username = "nue6",
              ping = 100L,
              userId = 3,
              connectionType = ConnectionType.BAD,
              status = UserStatus.IDLE
            ),
          ),
        games =
          listOf(
            ServerStatus.Game(
              romName = "My ROM",
              gameId = 100,
              clientType = "My N64 Emulator",
              username = "nue",
              playerCountOutOfMax = "2/4",
              GameStatus.PLAYING
            ),
            ServerStatus.Game(
              romName = "My ROM",
              gameId = 123,
              clientType = "My N64 Emulator",
              username = "nue2",
              playerCountOutOfMax = "2/4",
              GameStatus.PLAYING
            ),
            ServerStatus.Game(
              romName = "My ROM",
              gameId = 22,
              clientType = "My N64 Emulator",
              username = "nue3",
              playerCountOutOfMax = "2/4",
              GameStatus.SYNCHRONIZING
            ),
            ServerStatus.Game(
              romName = "My ROM",
              gameId = 5,
              clientType = "My N64 Emulator",
              username = "nue4",
              playerCountOutOfMax = "2/4",
              GameStatus.WAITING
            ),
          )
      )
  }
}
