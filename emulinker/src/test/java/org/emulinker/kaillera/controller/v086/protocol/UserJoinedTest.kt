package org.emulinker.kaillera.controller.v086.protocol

import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.model.ConnectionType

class UserJoinedTest : V086MessageTest<UserJoined>() {
  override val message =
    UserJoined(
      MESSAGE_NUMBER,
      username = "nue",
      userId = 13,
      ping = 999.milliseconds,
      connectionType = ConnectionType.LAN,
    )
  override val byteString = "6E, 75, 65, 00, 0D, 00, E7, 03, 00, 00, 01"
  override val serializer = UserJoined.UserJoinedSerializer
}
