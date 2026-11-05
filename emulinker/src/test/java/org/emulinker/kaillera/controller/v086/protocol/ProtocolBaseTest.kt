package org.emulinker.kaillera.controller.v086.protocol

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled
import java.nio.charset.Charset
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.testing.LoggingListener
import org.emulinker.util.ProtocolCharset

abstract class ProtocolBaseTest(body: FunSpec.() -> Unit = {}) :
  FunSpec({
    extension(LoggingListener())

    beforeSpec { ProtocolCharset.overrideForTesting(Charset.forName("Shift_JIS")) }

    body()
  }) {
  companion object {
    init {
      ProtocolCharset.overrideForTesting(Charset.forName("Shift_JIS"))
    }

    protected const val MESSAGE_NUMBER = 42
  }
}

abstract class ConnectMessageTest<K : ConnectMessage> :
  ProtocolBaseTest({
    val self = this as ConnectMessageTest<K>

    test("parse") {
      val byteBuf = Unpooled.buffer(4096)
      byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(self.byteString))

      val deserialized = ConnectMessage.parse(byteBuf)

      deserialized.getOrThrow() shouldBe self.message
      byteBuf.readableBytes() shouldBe 0
    }

    test("write") {
      val byteBuf = Unpooled.buffer(4096)
      self.message.writeTo(byteBuf)

      assertBufferContainsExactly(byteBuf, self.byteString)
    }
  }) {
  abstract val message: K
  abstract val byteString: String
}

abstract class V086MessageTest<K : V086Message> :
  ProtocolBaseTest({
    val self = this as V086MessageTest<K>

    test("bodyLength") {
      val byteBuffer = V086Utils.hexStringToByteBuffer(self.byteString)
      self.message.bodyBytes shouldBe byteBuffer.remaining()
    }

    test("read") {
      val byteBuf = Unpooled.buffer(4096)
      byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(self.byteString))

      val deserialized = self.serializer.read(byteBuf, self.message.messageNumber)

      deserialized.getOrThrow() shouldBe self.message
      byteBuf.readableBytes() shouldBe 0
    }

    test("write") {
      val byteBuf = Unpooled.buffer(4096)
      self.message.writeBodyTo(byteBuf)

      byteBuf.readableBytes() shouldBe self.message.bodyBytes
      assertBufferContainsExactly(byteBuf, self.byteString)
    }
  }) {
  abstract val message: K
  abstract val byteString: String
  abstract val serializer: MessageSerializer<K>
}
