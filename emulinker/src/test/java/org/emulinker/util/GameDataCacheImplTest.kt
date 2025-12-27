package org.emulinker.util

class GameDataCacheImplTest : GameDataCacheTest() {
  override val cache = GameDataCacheImpl(5)
}
