package org.emulinker.util

interface GameDataCache : Collection<VariableSizeByteArray> {
  operator fun get(index: Int): VariableSizeByteArray

  /**
   * Adds to the cache even the cache already contains it.
   *
   * Unfortunately some clients assume this behavior.
   */
  fun add(data: VariableSizeByteArray): Int

  fun indexOf(data: VariableSizeByteArray): Int

  fun clear()

  fun remove(index: Int)
}
