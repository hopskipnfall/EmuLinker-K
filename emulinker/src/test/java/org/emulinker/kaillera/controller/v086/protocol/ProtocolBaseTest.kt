package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import java.nio.charset.Charset
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

abstract class ProtocolBaseTest {
  @get:Rule val logging = LoggingRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }

    protected const val MESSAGE_NUMBER = 42
  }
}

abstract class ConnectMessageTest<K : ConnectMessage> : ProtocolBaseTest() {
  abstract val message: K
  abstract val byteString: String

  @Test
  fun parse() {
    val byteBuf = Unpooled.buffer(4096)
    byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(byteString))

    val deserialized = ConnectMessage.parse(byteBuf)

    assertThat(deserialized.getOrThrow()).isEqualTo(message)
    assertThat(byteBuf.readableBytes()).isEqualTo(0)
  }

  @Test
  fun write() {
    val byteBuf = Unpooled.buffer(4096)
    message.writeTo(byteBuf)

    assertBufferContainsExactly(byteBuf, byteString)
  }
}

abstract class V086MessageTest<K : V086Message> : ProtocolBaseTest() {
  abstract val message: K
  abstract val byteString: String
  abstract val serializer: MessageSerializer<K>

  @Test
  fun bodyLength() {
    val byteBuffer = V086Utils.hexStringToByteBuffer(byteString)
    assertThat(message.bodyBytes).isEqualTo(byteBuffer.remaining())
  }

  @Test
  fun read() {
    val byteBuf = Unpooled.buffer(4096)
    byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(byteString))

    val deserialized = serializer.read(byteBuf, message.messageNumber)

    assertThat(deserialized.getOrThrow()).isEqualTo(message)
    assertThat(byteBuf.readableBytes()).isEqualTo(0)
  }

  @Test
  fun write() {
    val byteBuf = Unpooled.buffer(4096)
    message.writeBodyTo(byteBuf)

    assertThat(byteBuf.readableBytes()).isEqualTo(message.bodyBytes)
    assertBufferContainsExactly(byteBuf, byteString)
  }
}
