package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Test

class UserJoinedTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(USER_JOINED.bodyBytes).isEqualTo(11)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    assertThat(
        UserJoined.UserJoinedSerializer.read(
            ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES)),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(USER_JOINED)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        UserJoined.UserJoinedSerializer.read(
            V086Utils.hexStringToByteBuffer(BODY_BYTES),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(USER_JOINED)
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    USER_JOINED.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(USER_JOINED.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES = "6E, 75, 65, 00, 00, 0D, 00, 00, 03, E7, 01"

    private val USER_JOINED =
      UserJoined(
        MESSAGE_NUMBER,
        username = "nue",
        userId = 13,
        ping = 999,
        connectionType = ConnectionType.LAN
      )
  }
}
