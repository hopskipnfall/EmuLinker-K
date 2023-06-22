package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class QuitGameTest : ProtocolBaseTest() {

  @Test
  fun quitGameNotification_bodyLength() {
    assertThat(QUIT_GAME_NOTIFICATION.bodyBytes).isEqualTo(6)
  }

  @Test
  fun quitGameNotification_deserializeBody() {
    assertThat(
        QuitGame.QuitGameSerializer.read(
          V086Utils.hexStringToByteBuffer(QUIT_GAME_NOTIFICATION_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(QUIT_GAME_NOTIFICATION))
  }

  @Test
  fun quitGameNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_GAME_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_GAME_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, QUIT_GAME_NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun quitGameRequest_bodyLength() {
    assertThat(QUIT_GAME_REQUEST.bodyBytes).isEqualTo(3)
  }

  @Test
  fun quitGameRequest_deserializeBody() {
    assertThat(
        QuitGame.QuitGameSerializer.read(
          V086Utils.hexStringToByteBuffer(QUIT_GAME_REQUEST_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(QUIT_GAME_REQUEST))
  }

  @Test
  fun quitGameRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, QUIT_GAME_REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val QUIT_GAME_REQUEST_BODY_BYTES = "00, FF, FF"
    private const val QUIT_GAME_NOTIFICATION_BODY_BYTES = "6E, 75, 65, 00, 00, 0D"

    private val QUIT_GAME_NOTIFICATION =
      QuitGameNotification(messageNumber = MESSAGE_NUMBER, username = "nue", userId = 13)
    private val QUIT_GAME_REQUEST = QuitGameRequest(messageNumber = MESSAGE_NUMBER)
  }
}
