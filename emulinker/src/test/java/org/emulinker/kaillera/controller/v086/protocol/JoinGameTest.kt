package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Ignore
import org.junit.Test

@Ignore
class JoinGameTest {

  @Test
  fun joinGameNotification_bodyLength() {
    assertThat(JOIN_GAME_NOTIFICATION.bodyBytes).isEqualTo(2)
  }

  @Test
  fun joinGameNotification_deserializeBody() {
    assertThat(JoinGame.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(JOIN_GAME_NOTIFICATION))
  }

  @Test
  fun joinGameNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    JOIN_GAME_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(JOIN_GAME_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  @Test
  fun joinGameRequest_bodyLength() {
    assertThat(JOIN_GAME_REQUEST.bodyBytes).isEqualTo(2)
  }

  @Test
  fun joinGameRequest_deserializeBody() {
    assertThat(JoinGame.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(JOIN_GAME_REQUEST))
  }

  @Test
  fun joinGameRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    JOIN_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(JOIN_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00,16"

    private val JOIN_GAME_NOTIFICATION =
      JoinGame.Notification(
        messageNumber = MESSAGE_NUMBER,
        gameId = 135,
        val1 = 1234,
        username = "nue",
        ping = 12353464565234,
        userId = 13,
        connectionType = ConnectionType.BAD
      )
    private val JOIN_GAME_REQUEST =
      JoinGame.Request(
        messageNumber = MESSAGE_NUMBER,
        gameId = 135,
        connectionType = ConnectionType.BAD
      )
  }
}
