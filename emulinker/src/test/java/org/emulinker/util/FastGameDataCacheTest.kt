package org.emulinker.util

class FastGameDataCacheTest : GameDataCacheTest() {
  override val cache = FastGameDataCache(5)
}
