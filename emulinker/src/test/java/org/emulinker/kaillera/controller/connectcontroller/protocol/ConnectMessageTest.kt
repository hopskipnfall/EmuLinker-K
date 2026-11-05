package org.emulinker.kaillera.controller.connectcontroller.protocol

import io.kotest.datatest.withTests
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest

class ConnectMessageTest :
  ProtocolBaseTest({
    test("invalidCase shouldResetPosition") {
      val byteBuf = Unpooled.buffer(4096)
      byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(UNKNOWN_BYTES))
      byteBuf.readableBytes() shouldBe 2

      val result = ConnectMessage.parse(byteBuf)

      result.isFailure.shouldBeTrue()
      byteBuf.readableBytes() shouldBe 2
    }

    context("malformed messages with known prefixes fail instead of throwing") {
      // Datagrams from unknown senders are parsed here first, so these must never throw.
      withTests(
        listOf("HELLO", "HELLO\u0000", "HELLOD00D", "PING", "PONG", "TOO", "HELLOD00D\u0000")
      ) { text ->
        val byteBuf = Unpooled.buffer(64)
        byteBuf.writeBytes(text.toByteArray(Charsets.ISO_8859_1))

        val result = ConnectMessage.parse(byteBuf)

        result.isFailure.shouldBeTrue()
        byteBuf.readableBytes() shouldBe text.length
      }
    }
  }) {

  private companion object {
    const val UNKNOWN_BYTES = "42, 42"
  }
}
