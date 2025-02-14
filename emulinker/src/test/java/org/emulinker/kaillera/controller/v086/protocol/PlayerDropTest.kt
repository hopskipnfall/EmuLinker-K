package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.endOfInput
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class PlayerDropTest {

  @Test
  fun playerDropNotification_bodyLength() {
    assertThat(PLAYER_DROP_NOTIFICATION.bodyBytes).isEqualTo(5)
  }

  @Test
  fun playerDropNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(PlayerDrop.PlayerDropSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(PLAYER_DROP_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun playerDropNotification_deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(PlayerDrop.PlayerDropSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(PLAYER_DROP_NOTIFICATION)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
  }

  @Test
  fun playerDropNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    PLAYER_DROP_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(PLAYER_DROP_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun playerDropRequest_bodyLength() {
    assertThat(PLAYER_DROP_REQUEST.bodyBytes).isEqualTo(2)
  }

  @Test
  fun playerDropRequest_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(PlayerDrop.PlayerDropSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(PLAYER_DROP_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun playerDropRequest_deserializeBody() {
    val buffer = Unpooled.wrappedBuffer(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(PlayerDrop.PlayerDropSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(PLAYER_DROP_REQUEST)
    assertThat(buffer.capacity()).isEqualTo(buffer.readerIndex())
  }

  @Test
  fun playerDropRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    PLAYER_DROP_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(PLAYER_DROP_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val REQUEST_BODY_BYTES = "00, 00"
    private const val NOTIFICATION_BODY_BYTES = "6E, 75, 65, 00, 64"

    private val PLAYER_DROP_NOTIFICATION =
      PlayerDropNotification(messageNumber = MESSAGE_NUMBER, username = "nue", playerNumber = 100)
    private val PLAYER_DROP_REQUEST = PlayerDropRequest(messageNumber = MESSAGE_NUMBER)
  }
}
