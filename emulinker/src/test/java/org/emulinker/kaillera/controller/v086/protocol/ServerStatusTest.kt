package org.emulinker.kaillera.controller.v086.protocol

import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.UserStatus

class ServerStatusTest : V086MessageTest<ServerStatus>() {
  override val message =
    ServerStatus(
      MESSAGE_NUMBER,
      users =
        listOf(
          ServerStatus.User(
            username = "nue",
            ping = 100L.milliseconds,
            userId = 13,
            connectionType = ConnectionType.LAN,
            status = UserStatus.CONNECTING,
          ),
          ServerStatus.User(
            username = "nue1",
            ping = 100L.milliseconds,
            userId = 14,
            connectionType = ConnectionType.LAN,
            status = UserStatus.IDLE,
          ),
          ServerStatus.User(
            username = "nue2",
            ping = 100L.milliseconds,
            userId = 18,
            connectionType = ConnectionType.AVERAGE,
            status = UserStatus.PLAYING,
          ),
          ServerStatus.User(
            username = "nue3",
            ping = 100L.milliseconds,
            userId = 200,
            connectionType = ConnectionType.LAN,
            status = UserStatus.CONNECTING,
          ),
          ServerStatus.User(
            username = "nue4",
            ping = 100L.milliseconds,
            userId = 12,
            connectionType = ConnectionType.LAN,
            status = UserStatus.PLAYING,
          ),
          ServerStatus.User(
            username = "nue5",
            ping = 100L.milliseconds,
            userId = 8,
            connectionType = ConnectionType.BAD,
            status = UserStatus.CONNECTING,
          ),
          ServerStatus.User(
            username = "nue6",
            ping = 100L.milliseconds,
            userId = 3,
            connectionType = ConnectionType.BAD,
            status = UserStatus.IDLE,
          ),
        ),
      games =
        listOf(
          ServerStatus.Game(
            romName = "My ROM",
            gameId = 100,
            clientType = "My N64 Emulator",
            username = "nue",
            playerCountOutOfMax = "2/4",
            GameStatus.PLAYING,
          ),
          ServerStatus.Game(
            romName = "My ROM",
            gameId = 123,
            clientType = "My N64 Emulator",
            username = "nue2",
            playerCountOutOfMax = "2/4",
            GameStatus.PLAYING,
          ),
          ServerStatus.Game(
            romName = "My ROM",
            gameId = 22,
            clientType = "My N64 Emulator",
            username = "nue3",
            playerCountOutOfMax = "2/4",
            GameStatus.SYNCHRONIZING,
          ),
          ServerStatus.Game(
            romName = "My ROM",
            gameId = 5,
            clientType = "My N64 Emulator",
            username = "nue4",
            playerCountOutOfMax = "2/4",
            GameStatus.WAITING,
          ),
        ),
    )
  override val byteString =
    "00, 07, 00, 00, 00, 04, 00, 00, 00, 6E, 75, 65, 00, 64, 00, 00, 00, 02, 0D, 00, 01, 6E, 75, 65, 31, 00, 64, 00, 00, 00, 01, 0E, 00, 01, 6E, 75, 65, 32, 00, 64, 00, 00, 00, 00, 12, 00, 04, 6E, 75, 65, 33, 00, 64, 00, 00, 00, 02, C8, 00, 01, 6E, 75, 65, 34, 00, 64, 00, 00, 00, 00, 0C, 00, 01, 6E, 75, 65, 35, 00, 64, 00, 00, 00, 02, 08, 00, 06, 6E, 75, 65, 36, 00, 64, 00, 00, 00, 01, 03, 00, 06, 4D, 79, 20, 52, 4F, 4D, 00, 64, 00, 00, 00, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 00, 32, 2F, 34, 00, 02, 4D, 79, 20, 52, 4F, 4D, 00, 7B, 00, 00, 00, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 32, 00, 32, 2F, 34, 00, 02, 4D, 79, 20, 52, 4F, 4D, 00, 16, 00, 00, 00, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 33, 00, 32, 2F, 34, 00, 01, 4D, 79, 20, 52, 4F, 4D, 00, 05, 00, 00, 00, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 34, 00, 32, 2F, 34, 00, 00"
  override val serializer = ServerStatus.ServerStatusSerializer
}
