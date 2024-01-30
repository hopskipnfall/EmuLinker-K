package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Test

class UserInformationTest : ProtocolBaseTest() {

  @Test
  fun bodyLength() {
    assertThat(USER_INFORMATION.bodyBytes).isEqualTo(17)
  }

  @Test
  fun byteReadPacket_deserializeBody() {
    assertThat(
        UserInformation.UserInformationSerializer.read(
            ByteReadPacket(V086Utils.hexStringToByteBuffer(BODY_BYTES)),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(USER_INFORMATION)
  }

  @Test
  fun deserializeBody() {
    assertThat(
        UserInformation.UserInformationSerializer.read(
            V086Utils.hexStringToByteBuffer(BODY_BYTES),
            MESSAGE_NUMBER
          )
          .getOrThrow()
      )
      .isEqualTo(USER_INFORMATION)
  }

  @Test
  fun serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    USER_INFORMATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(USER_INFORMATION.bodyBytes)
    assertBufferContainsExactly(buffer, BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val BODY_BYTES =
      "6E, 75, 65, 00, 4D, 79, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 01"

    private val USER_INFORMATION =
      UserInformation(
        MESSAGE_NUMBER,
        username = "nue",
        clientType = "My Emulator",
        connectionType = ConnectionType.LAN
      )
  }
}
