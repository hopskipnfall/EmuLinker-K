package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
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
    val buffer = V086Utils.hexStringToByteBuffer(QUIT_GAME_NOTIFICATION_BODY_BYTES)
    assertThat(QuitGame.QuitGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_GAME_NOTIFICATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun quitGameNotification_serializeBody() {
    val buffer = allocateByteBuffer()
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
    val buffer = V086Utils.hexStringToByteBuffer(QUIT_GAME_REQUEST_BODY_BYTES)
    assertThat(QuitGame.QuitGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_GAME_REQUEST)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun quitGameRequest_serializeBody() {
    val buffer = allocateByteBuffer()
    QUIT_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, QUIT_GAME_REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val QUIT_GAME_REQUEST_BODY_BYTES = "00, FF, FF"
    private const val QUIT_GAME_NOTIFICATION_BODY_BYTES = "6E, 75, 65, 00, 0D, 00"

    private val QUIT_GAME_NOTIFICATION =
      QuitGameNotification(messageNumber = MESSAGE_NUMBER, username = "nue", userId = 13)
    private val QUIT_GAME_REQUEST = QuitGameRequest(messageNumber = MESSAGE_NUMBER)
  }
}
