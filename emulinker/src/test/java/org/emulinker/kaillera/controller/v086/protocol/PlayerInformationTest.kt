package org.emulinker.kaillera.controller.v086.protocol

import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.model.ConnectionType

class PlayerInformationTest : V086MessageTest<PlayerInformation>() {
  override val message =
    PlayerInformation(
      MESSAGE_NUMBER,
      players =
        listOf(
          PlayerInformation.Player(
            username = "nue",
            ping = 100L.milliseconds,
            userId = 13,
            connectionType = ConnectionType.LAN,
          ),
          PlayerInformation.Player(
            username = "nue1",
            ping = 100L.milliseconds,
            userId = 14,
            connectionType = ConnectionType.LAN,
          ),
          PlayerInformation.Player(
            username = "nue2",
            ping = 100L.milliseconds,
            userId = 18,
            connectionType = ConnectionType.AVERAGE,
          ),
          PlayerInformation.Player(
            username = "nue3",
            ping = 100L.milliseconds,
            userId = 200,
            connectionType = ConnectionType.LAN,
          ),
          PlayerInformation.Player(
            username = "nue4",
            ping = 100L.milliseconds,
            userId = 12,
            connectionType = ConnectionType.LAN,
          ),
          PlayerInformation.Player(
            username = "nue5",
            ping = 100L.milliseconds,
            userId = 8,
            connectionType = ConnectionType.BAD,
          ),
          PlayerInformation.Player(
            username = "nue6",
            ping = 100.milliseconds,
            userId = 3,
            connectionType = ConnectionType.BAD,
          ),
        ),
    )
  override val byteString =
    "00, 07, 00, 00, 00, 6E, 75, 65, 00, 64, 00, 00, 00, 0D, 00, 01, 6E, 75, 65, 31, 00, 64, 00, 00, 00, 0E, 00, 01, 6E, 75, 65, 32, 00, 64, 00, 00, 00, 12, 00, 04, 6E, 75, 65, 33, 00, 64, 00, 00, 00, C8, 00, 01, 6E, 75, 65, 34, 00, 64, 00, 00, 00, 0C, 00, 01, 6E, 75, 65, 35, 00, 64, 00, 00, 00, 08, 00, 06, 6E, 75, 65, 36, 00, 64, 00, 00, 00, 03, 00, 06"
  override val serializer = PlayerInformation.PlayerInformationSerializer
}
