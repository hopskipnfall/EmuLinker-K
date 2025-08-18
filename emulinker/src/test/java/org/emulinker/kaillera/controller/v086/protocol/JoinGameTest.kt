package org.emulinker.kaillera.controller.v086.protocol

import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.model.ConnectionType

class JoinGameRequestTest : V086MessageTest<JoinGame>() {
  override val message =
    JoinGameRequest(
      messageNumber = MESSAGE_NUMBER,
      gameId = 135,
      connectionType = ConnectionType.BAD,
    )
  override val byteString = "00, 87, 00, 00, 00, 00, 00, 00, 00, 00, FF, FF, 06"
  override val serializer = JoinGame.JoinGameSerializer
}

class JoinGameNotificationTest : V086MessageTest<JoinGame>() {
  override val message =
    JoinGameNotification(
      messageNumber = MESSAGE_NUMBER,
      gameId = 135,
      val1 = 1234,
      username = "nue",
      ping = 1235.milliseconds,
      userId = 13,
      connectionType = ConnectionType.BAD,
    )
  override val byteString = "00, 87, 00, D2, 04, 6E, 75, 65, 00, D3, 04, 00, 00, 0D, 00, 06"
  override val serializer = JoinGame.JoinGameSerializer
}
