package org.emulinker.util

import io.netty.buffer.ByteBuf

interface GameDataCache : Collection<ByteBuf> {
  operator fun get(index: Int): ByteBuf

  /**
   * Adds to the cache even the cache already contains it.
   *
   * Unfortunately some clients assume this behavior.
   */
  fun add(data: ByteBuf): Int

  fun indexOf(data: ByteBuf): Int

  fun clear()

  fun remove(index: Int)

  val capacity: Int
}
