package org.emulinker.kaillera.controller.v086.protocol

import io.netty.buffer.Unpooled

class GameDataTest : V086MessageTest<GameData>() {
  override val message =
    GameData(
      messageNumber = MESSAGE_NUMBER,
      gameData = Unpooled.wrappedBuffer(byteArrayOf(2, 3, 4, 5, 6)),
    )
  override val byteString = "00, 05, 00, 02, 03, 04, 05, 06"
  override val serializer = GameData.GameDataSerializer
}
