package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class QuitTest : ProtocolBaseTest() {

  @Test
  fun quitNotification_bodyLength() {
    assertThat(QUIT_NOTIFICATION.bodyBytes).isEqualTo(20)
  }

  @Test
  fun quitNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(Quit.QuitSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun quitNotification_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES)
    assertThat(Quit.QuitSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_NOTIFICATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun quitNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun quitRequest_bodyLength() {
    assertThat(QUIT_REQUEST.bodyBytes).isEqualTo(17)
  }

  @Test
  fun quitRequest_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(Quit.QuitSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun quitRequest_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES)
    assertThat(Quit.QuitSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(QUIT_REQUEST)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun quitRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val NOTIFICATION_BODY_BYTES =
      "6E, 75, 65, 00, 00, 0D, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"
    private const val REQUEST_BODY_BYTES =
      "00, FF, FF, 48, 65, 6C, 6C, 6F, 2C, 20, 77, 6F, 72, 6C, 64, 21, 00"

    private val QUIT_NOTIFICATION =
      QuitNotification(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        userId = 13,
        message = "Hello, world!"
      )
    private val QUIT_REQUEST =
      QuitRequest(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  }
}
