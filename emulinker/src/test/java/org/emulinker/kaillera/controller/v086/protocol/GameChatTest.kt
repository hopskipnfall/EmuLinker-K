package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
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
  fun gameChatNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(GameChat.GameChatSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_CHAT_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun gameChatNotification_deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(GameChat.GameChatSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_CHAT_NOTIFICATION)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
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
  fun gameChatRequest_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(GameChat.GameChatSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_CHAT_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun gameChatRequest_deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(GameChat.GameChatSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(GAME_CHAT_REQUEST)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
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
        message = "Hello, world!",
      )
    private val GAME_CHAT_REQUEST =
      GameChatRequest(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  }
}
