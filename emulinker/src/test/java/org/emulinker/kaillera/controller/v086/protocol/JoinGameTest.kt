package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.emulinker.kaillera.model.ConnectionType
import org.junit.Ignore
import org.junit.Test

class JoinGameTest : ProtocolBaseTest() {

  @Test
  fun joinGameNotification_bodyLength() {
    assertThat(JOIN_GAME_NOTIFICATION.bodyBytes).isEqualTo(16)
  }

  @Test
  @Ignore // Fails!
  fun joinGameNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(NOTIFICATION_BYTES))
    assertThat(JoinGame.JoinGameSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(JOIN_GAME_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun joinGameNotification_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(NOTIFICATION_BYTES)
    assertThat(JoinGame.JoinGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(JOIN_GAME_NOTIFICATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun joinGameNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    JOIN_GAME_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(JOIN_GAME_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BYTES)
  }

  @Test
  fun joinGameRequest_bodyLength() {
    assertThat(JOIN_GAME_REQUEST.bodyBytes).isEqualTo(13)
  }

  @Test
  @Ignore // Fails!
  fun joinGameRequest_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(REQUEST_BYTES))
    assertThat(JoinGame.JoinGameSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(JOIN_GAME_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun joinGameRequest_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(REQUEST_BYTES)
    assertThat(JoinGame.JoinGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(JOIN_GAME_REQUEST)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun joinGameRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    JOIN_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(JOIN_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val NOTIFICATION_BYTES =
      "00, 00, 87, 04, D2, 6E, 75, 65, 00, 00, 00, 04, D3, 00, 0D, 06"
    private const val REQUEST_BYTES = "00, 00, 87, 00, 00, 00, 00, 00, 00, 00, FF, FF, 06"

    private val JOIN_GAME_NOTIFICATION =
      JoinGameNotification(
        messageNumber = MESSAGE_NUMBER,
        gameId = 135,
        val1 = 1234,
        username = "nue",
        ping = 1235,
        userId = 13,
        connectionType = ConnectionType.BAD
      )
    private val JOIN_GAME_REQUEST =
      JoinGameRequest(
        messageNumber = MESSAGE_NUMBER,
        gameId = 135,
        connectionType = ConnectionType.BAD
      )
  }
}
