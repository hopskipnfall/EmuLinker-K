package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Ignore
import org.junit.Test

@Ignore
class UserInformationTest {

  @Test
  fun bodyLength() {
    assertThat(USER_INFORMATION.bodyBytes).isEqualTo(2)
  }

  @Test
  fun deserializeBody() {
    assertThat(UserInformation.parse(MESSAGE_NUMBER, V086Utils.hexStringToByteBuffer(BODY_BYTES)))
      .isEqualTo(MessageParseResult.Success(USER_INFORMATION))
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
    private const val BODY_BYTES = "00,16"

    private val USER_INFORMATION =
      UserInformation(
        MESSAGE_NUMBER,
        username = "nue",
        clientType = "My Emulator",
        connectionType = ConnectionType.LAN
      )
  }
}
