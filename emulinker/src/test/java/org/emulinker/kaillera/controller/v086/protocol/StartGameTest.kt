package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.core.ByteReadPacket
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.MessageTestUtils.assertBufferContainsExactly
import org.junit.Test

class StartGameTest : ProtocolBaseTest() {

  @Test
  fun startGameNotification_bodyLength() {
    assertThat(START_GAME_NOTIFICATION.bodyBytes).isEqualTo(5)
  }

  @Test
  fun startGameNotification_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES))
    assertThat(StartGame.StartGameSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(START_GAME_NOTIFICATION)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun startGameNotification_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(NOTIFICATION_BODY_BYTES)
    assertThat(StartGame.StartGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(START_GAME_NOTIFICATION)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun startGameNotification_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    START_GAME_NOTIFICATION.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(START_GAME_NOTIFICATION.bodyBytes)
    assertBufferContainsExactly(buffer, NOTIFICATION_BODY_BYTES)
  }

  @Test
  fun startGameRequest_bodyLength() {
    assertThat(START_GAME_REQUEST.bodyBytes).isEqualTo(5)
  }

  @Test
  fun startGameRequest_byteReadPacket_deserializeBody() {
    val packet = ByteReadPacket(V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES))
    assertThat(StartGame.StartGameSerializer.read(packet, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(START_GAME_REQUEST)
    assertThat(packet.endOfInput).isTrue()
  }

  @Test
  fun startGameRequest_deserializeBody() {
    val buffer = V086Utils.hexStringToByteBuffer(REQUEST_BODY_BYTES)
    assertThat(StartGame.StartGameSerializer.read(buffer, MESSAGE_NUMBER).getOrThrow())
      .isEqualTo(START_GAME_REQUEST)
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun startGameRequest_serializeBody() {
    val buffer = ByteBuffer.allocateDirect(4096)
    START_GAME_REQUEST.writeBodyTo(buffer)

    assertThat(buffer.position()).isEqualTo(START_GAME_REQUEST.bodyBytes)
    assertBufferContainsExactly(buffer, REQUEST_BODY_BYTES)
  }

  companion object {
    private const val MESSAGE_NUMBER = 42
    private const val NOTIFICATION_BODY_BYTES = "00, 07, D0, 2A, 04"
    private const val REQUEST_BODY_BYTES = "00, FF, FF, FF, FF"

    private val START_GAME_NOTIFICATION =
      StartGameNotification(
        messageNumber = MESSAGE_NUMBER,
        numPlayers = 4,
        playerNumber = 42,
        val1 = 2000
      )
    private val START_GAME_REQUEST = StartGameRequest(messageNumber = MESSAGE_NUMBER)
  }
}
