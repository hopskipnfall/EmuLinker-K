package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.controller.v086.action.ACKAction
import org.junit.Test

class ClientAckTest : V086MessageTest<ClientAck>() {
  override val message = ClientAck(MESSAGE_NUMBER)
  override val byteString = "00, 00, 00, 00, 00, 01, 00, 00, 00, 02, 00, 00, 00, 03, 00, 00, 00"
  override val serializer = Ack.ClientAckSerializer

  @Test
  fun extractFakePingFromUsername_valid() {
    assertThat(ACKAction.extractFakePingFromUsername("test_exp_fakeping=50"))
      .isEqualTo(50.milliseconds)
  }

  @Test
  fun extractFakePingFromUsername_caseInsensitive() {
    assertThat(ACKAction.extractFakePingFromUsername("test_EXP_FAKEPING=100"))
      .isEqualTo(100.milliseconds)
  }

  @Test
  fun extractFakePingFromUsername_simpleUsernameReturnsNull() {
    assertThat(ACKAction.extractFakePingFromUsername("simple_username")).isNull()
  }

  @Test
  fun extractFakePingFromUsername_tooManyDigitsReturnsNull() {
    assertThat(ACKAction.extractFakePingFromUsername("test_exp_fakeping=1234")).isNull()
  }

  @Test
  fun extractFakePingFromUsername_validWithNoPrefix() {
    assertThat(ACKAction.extractFakePingFromUsername("exp_fakeping=123"))
      .isEqualTo(123.milliseconds)
  }
}

class ServerAckTest : V086MessageTest<ServerAck>() {
  override val message = ServerAck(MESSAGE_NUMBER)
  override val byteString = "00, 00, 00, 00, 00, 01, 00, 00, 00, 02, 00, 00, 00, 03, 00, 00, 00"
  override val serializer = Ack.ServerAckSerializer
}
