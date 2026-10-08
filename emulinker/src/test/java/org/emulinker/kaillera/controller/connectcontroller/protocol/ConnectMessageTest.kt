package org.emulinker.kaillera.controller.connectcontroller.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.junit.Test

class ConnectMessageTest : ProtocolBaseTest() {

  @Test
  fun invalidCase_shouldResetPosition() {
    val byteBuf = Unpooled.buffer(4096)
    byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(UNKNOWN_BYTES))
    assertThat(byteBuf.readableBytes()).isEqualTo(2)

    val result = ConnectMessage.parse(byteBuf)

    assertThat(result.isFailure)
    assertThat(byteBuf.readableBytes()).isEqualTo(2)
  }

  @Test
  fun malformedMessagesWithKnownPrefixesFailInsteadOfThrowing() {
    // Datagrams from unknown senders are parsed here first, so these must never throw.
    for (text in
      listOf("HELLO", "HELLO\u0000", "HELLOD00D", "PING", "PONG", "TOO", "HELLOD00D\u0000")) {
      val byteBuf = Unpooled.buffer(64)
      byteBuf.writeBytes(text.toByteArray(Charsets.ISO_8859_1))

      val result = ConnectMessage.parse(byteBuf)

      assertThat(result.isFailure).isTrue()
      assertThat(byteBuf.readableBytes()).isEqualTo(text.length)
    }
  }

  private companion object {
    const val UNKNOWN_BYTES = "42, 42"
  }
}
