package org.emulinker.util

interface GameDataCache : Collection<VariableSizeByteArray> {
  operator fun get(index: Int): VariableSizeByteArray

  fun add(data: VariableSizeByteArray): Int

  fun indexOf(data: VariableSizeByteArray): Int

  fun clear()

  fun remove(index: Int)
}
