package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Ignore
import org.junit.Test

@Ignore
class QuitTest {

  @Test
  fun quitNotification_bodyLength() {
    assertThat(QUIT_NOTIFICATION.bodyBytes).isEqualTo(2)
  }

  @Test
  fun quitNotification_deserializeBody() {
    assertThat(Quit.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(QUIT_NOTIFICATION))
  }

  @Test
  fun quitNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  @Test
  fun quitRequest_bodyLength() {
    assertThat(QUIT_REQUEST.bodyBytes).isEqualTo(2)
  }

  @Test
  fun quitRequest_deserializeBody() {
    assertThat(Quit.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(QUIT_REQUEST))
  }

  @Test
  fun quitRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    QUIT_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(QUIT_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "00,16"

    private val QUIT_NOTIFICATION =
      Quit.Notification(
        messageNumber = MESSAGE_NUMBER,
        username = "nue",
        userId = 13,
        message = "Hello, world!"
      )
    private val QUIT_REQUEST =
      Quit.Request(messageNumber = MESSAGE_NUMBER, message = "Hello, world!")
  }
}
