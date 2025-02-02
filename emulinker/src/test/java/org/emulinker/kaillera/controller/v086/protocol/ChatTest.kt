package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils.hexStringToByteBuffer
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class ChatTest : ProtocolBaseTest() {

  @Test
  fun chatNotification_bodyLength() {
    assertThat(CHAT_NOTIFICATION.bodyBytes).isEqualTo(18)
  }

  @Test
  fun chatNotification_deserializeBody() {
    val buffer = hexStringToByteBuffer(CHAT_NOTIFICATION_BODY_BYTES)
    assertThat(Chat.ChatSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CHAT_NOTIFICATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun chatNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(hexStringToByteBuffer(CHAT_NOTIFICATION_BODY_BYTES))
    assertThat(Chat.ChatSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CHAT_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun chatNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CHAT_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CHAT_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, CHAT_NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun chatNotification_serializeBody_byteBuf() {
    val buffer = Unpooled.buffer(4096)
    CHAT_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.readableBytes()).isEqualTo(CHAT_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, CHAT_NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun bodyLength() {
    assertThat(CHAT_REQUEST.bodyBytes).isEqualTo(15)
  }

  @Test
  fun deserializeBody() {
    val buffer = hexStringToByteBuffer(CHAT_REQUEST_BODY_BYTES)
    assertThat(Chat.ChatSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CHAT_REQUEST)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(hexStringToByteBuffer(CHAT_REQUEST_BODY_BYTES))
    assertThat(Chat.ChatSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CHAT_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    CHAT_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CHAT_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, CHAT_REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val USERNAME = "nue"
    private const val MESSAGE = "Hello, world!"

    private const val CHAT_NOTIFICATION_BODY_BYTES =
      "6E,75,65,00,48,65,6C,6C,6F,2C,20,77,6F,72,6C,64,21,00"
    private const val CHAT_REQUEST_BODY_BYTES = "00,48,65,6C,6C,6F,2C,20,77,6F,72,6C,64,21,00"

    private val CHAT_NOTIFICATION =
      ChatNotification(messageNumber = MESSAGE_NUMBER, username = USERNAME, message = MESSAGE)
    private val CHAT_REQUEST = ChatRequest(messageNumber = MESSAGE_NUMBER, message = MESSAGE)
  }
}
