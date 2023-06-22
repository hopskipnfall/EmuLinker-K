package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class CreateGameTest : ProtocolBaseTest() {

  @Test
  fun createGameNotification_bodyLength() {
    assertThat(CREATE_GAME_NOTIFICATION.bodyBytes).isEqualTo(32)
  }

  @Test
  fun createGameNotification_deserializeBody() {
    assertThat(
        CreateGame.CreateGameSerializer.read(
          V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(CREATE_GAME_NOTIFICATION))
  }

  @Test
  fun createGameNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CREATE_GAME_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CREATE_GAME_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun createGameRequest_bodyLength() {
    assertThat(CREATE_GAME_REQUEST.bodyBytes).isEqualTo(14)
  }

  @Test
  fun createGameRequest_deserializeBody() {
    assertThat(
        CreateGame.CreateGameSerializer.read(
          V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(CREATE_GAME_REQUEST))
  }

  @Test
  fun createGameRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CREATE_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CREATE_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val REQUEST_BODY_BYTES = "00, 4D, 79, 20, 47, 61, 6D, 65, 00, 00, FF, FF, FF, FF"
    private const val NOTIFICATION_BODY_BYTES =
      "6E, 75, 65, 00, 4D, 79, 20, 47, 61, 6D, 65, 00, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 00, 64, 10, 92"

    private val CREATE_GAME_NOTIFICATION =
      CreateGameNotification(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        romName = "My Game",
        clientType = "My N64 Emulator",
        gameId = 100,
        val1 = 4242
      )
    private val CREATE_GAME_REQUEST =
      CreateGameRequest(messageNumber = MESSAGE_NUMBER, romName = "My Game")
  }
}
