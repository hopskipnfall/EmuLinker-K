package org.emulinker.util

interface GameDataCache : Collection<ByteArray> {
  operator fun get(index: Int): ByteArray?

  operator fun set(index: Int, data: ByteArray): ByteArray?

  fun add(data: ByteArray): Int

  fun indexOf(data: ByteArray): Int

  fun clear()

  fun remove(index: Int): ByteArray?
}
