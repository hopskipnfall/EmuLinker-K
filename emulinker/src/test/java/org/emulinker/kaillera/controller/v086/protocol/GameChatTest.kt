package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class GameChatTest : ProtocolBaseTest() {

  @Test
  fun gameChatNotification_bodyLength() {
    assertThat(GAME_CHAT_NOTIFICATION.bodyBytes).isEqualTo(18)
  }

  @Test
  fun gameChatNotification_deserializeBody() {
    assertThat(
        GameChat.GameChatSerializer.read(
          V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(GAME_CHAT_NOTIFICATION))
  }

  @Test
  fun gameChatNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    GAME_CHAT_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_CHAT_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun gameChatRequest_bodyLength() {
    assertThat(GAME_CHAT_REQUEST.bodyBytes).isEqualTo(15)
  }

  @Test
  fun gameChatRequest_deserializeBody() {
    assertThat(
        GameChat.GameChatSerializer.read(
          V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES),
          MESSAGE_NUMBER
        )
      )
      .isEqualTo(MessageParseResult.Success(GAME_CHAT_REQUEST))
  }

  @Test
  fun gameChatRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    GAME_CHAT_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(GAME_CHAT_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val NOTIFICATION_BODY_BYTES =
      "6E, 75, 65, 00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
    private const val REQUEST_BODY_BYTES =
      "00, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"

    private val GAME_CHAT_NOTIFICATION =
      GameChatNotification(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        message = "Hello, world!"
      )
    private val GAME_CHAT_REQUEST =
      GameChatRequest(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  }
}
