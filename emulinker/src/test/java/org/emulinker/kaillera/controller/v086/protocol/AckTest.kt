package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class AckTest : ProtocolBaseTest() {

  @Test
  fun clientAck_bodyLength() {
    assertThat(CLIENT_ACK.bodyBytes).isEqualTo(17)
  }

  @Test
  fun clientAck_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(ACK_BODY_BYTES)
    assertThat(Ack.ClientAckSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CLIENT_ACK)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun clientAck_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(ACK_BODY_BYTES))
    assertThat(Ack.ClientAckSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(CLIENT_ACK)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun clientAck_serializeBody() {
    val buffer = ByteBuffer.allocate(4096)
    CLIENT_ACK.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(CLIENT_ACK.bodyBytes)
    assertBufferContainsExactly(buffer, ACK_BODY_BYTES)
  }

  @Test
  fun clientAck_serializeBody_byteBuf() {
    val buffer = Unpooled.buffer(4096)

    Ack.ClientAckSerializer.write(buffer, CLIENT_ACK)

    assertThat(buffer.readableBytes()).isEqualTo(CLIENT_ACK.bodyBytes)
    assertBufferContainsExactly(buffer, ACK_BODY_BYTES)
  }

  @Test
  fun serverAck_bodyLength() {
    assertThat(SERVER_ACK.bodyBytes).isEqualTo(17)
  }

  @Test
  fun serverAck_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(ACK_BODY_BYTES)
    assertThat(Ack.ServerAckSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(SERVER_ACK)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun serverAck_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(ACK_BODY_BYTES))
    assertThat(Ack.ServerAckSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(SERVER_ACK)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun serverAck_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    SERVER_ACK.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(SERVER_ACK.bodyBytes)
    assertBufferContainsExactly(buffer, ACK_BODY_BYTES)
  }

  @Test
  fun serverAck_serializeBody_byteBuf() {
    val buffer = Unpooled.buffer(4096)
    SERVER_ACK.writeBodyTo(buffer)

    assertThat(buffer.readableBytes()).isEqualTo(SERVER_ACK.bodyBytes)
    assertBufferContainsExactly(buffer, ACK_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42

    // The body bytes are identical, but the message type IDs (which is not included in the body)
    // are different.
    private const val ACK_BODY_BYTES = "00,00,00,00,00,00,00,00,01,00,00,00,02,00,00,00,03"

    private val CLIENT_ACK = ClientAck(MESSAGE_NUMBER)
    private val SERVER_ACK = ServerAck(MESSAGE_NUMBER)
  }
}
