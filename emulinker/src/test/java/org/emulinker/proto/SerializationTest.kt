package org.emulinker.proto

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.protobuf.util.Timestamps
import java.io.FileInputStream
import org.emulinker.proto.EventKt.GameStartKt.playerDetails
import org.emulinker.proto.EventKt.fanOut
import org.emulinker.proto.EventKt.gameStart
import org.emulinker.proto.EventKt.receivedGameData
import org.junit.Test

class SerializationTest {
  @Test
  fun deserializeGameLog() {
    val log: GameLog =
      FileInputStream("src/test/resources/testgamelog.bin").use {
        GameLog.parseFrom(it.readBytes())
      }

    assertThat(log)
      .isEqualTo(
        gameLog {
          events += event {
            timestampNs = 1
            gameStart = gameStart {
              timestamp = Timestamps.fromNanos(12345)
              players += playerDetails {
                playerNumber = Player.PLAYER_ONE
                pingMs = 12.0
                frameDelay = 1
              }
            }
          }
          events += event {
            timestampNs = 2
            receivedGameData = receivedGameData { receivedFrom = Player.PLAYER_ONE }
          }
          events += event {
            timestampNs = 3
            fanOut = fanOut {}
          }
        }
      )
  }
}
