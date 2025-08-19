package org.emulinker.kaillera.controller.v086.protocol

class CachedGameDataTest : V086MessageTest<CachedGameData>() {
  override val message = CachedGameData(42, key = 12)
  override val byteString = "00, 0C"
  override val serializer = CachedGameData.CachedGameDataSerializer
}
