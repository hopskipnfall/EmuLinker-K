package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.Charset
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

abstract class ProtocolBaseTest {
  @get:Rule val logging = LoggingRule()

  protected fun allocateByteBuffer() = ByteBuffer.allocate(4096).order(LITTLE_ENDIAN)

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }
  }
}

abstract class V086MessageTest<K : V086Message> {
  @get:Rule val logging = LoggingRule()

  abstract val message: K
  abstract val byteString: String
  abstract val serializer: MessageSerializer<K>

  @Test
  fun deserialize_byteBuffer() {
    val byteBuffer = V086Utils.hexStringToByteBuffer(byteString)

    val deserialized = serializer.read(byteBuffer, message.messageNumber)

    assertThat(deserialized.getOrThrow()).isEqualTo(message)
    assertThat(byteBuffer.hasRemaining()).isFalse()
  }

  @Test
  fun read_byteBuffer() {
    val byteBuffer = ByteBuffer.allocate(4096).order(LITTLE_ENDIAN)

    message.writeBodyTo(byteBuffer)

    assertThat(byteBuffer.position()).isEqualTo(message.bodyBytes)
    assertBufferContainsExactly(byteBuffer, byteString)
  }

  @Test
  fun bodyLength() {
    val byteBuffer = V086Utils.hexStringToByteBuffer(byteString)
    assertThat(message.bodyBytes).isEqualTo(byteBuffer.remaining())
  }

  @Test
  fun read_byteBuf() {
    val byteBuf = Unpooled.buffer(4096)
    byteBuf.writeBytes(V086Utils.hexStringToByteBuffer(byteString))

    val deserialized = serializer.read(byteBuf, message.messageNumber)

    assertThat(deserialized.getOrThrow()).isEqualTo(message)
    assertThat(byteBuf.readableBytes()).isEqualTo(0)
  }

  @Test
  fun serialize_byteBuf() {
    val byteBuf = Unpooled.buffer(4096)
    message.writeBodyTo(byteBuf)

    assertThat(byteBuf.readableBytes()).isEqualTo(message.bodyBytes)
    assertBufferContainsExactly(byteBuf, byteString)
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")
    }

    protected const val MESSAGE_NUMBER = 42
  }
}
