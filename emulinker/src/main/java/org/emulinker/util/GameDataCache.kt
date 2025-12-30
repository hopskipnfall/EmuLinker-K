package org.emulinker.util

import io.netty.buffer.ByteBuf

interface GameDataCache : Iterable<ByteBuf> {
  operator fun get(index: Int): ByteBuf

  operator fun contains(data: ByteBuf): Boolean

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

  val size: Int

  fun isEmpty(): Boolean
}
