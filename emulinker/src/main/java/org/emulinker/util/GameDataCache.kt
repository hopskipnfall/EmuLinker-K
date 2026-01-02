package org.emulinker.util

import io.netty.buffer.ByteBuf

interface GameDataCache : Collection<ByteBuf> {
  /** Returns the element at the specified index in the cache. */
  operator fun get(index: Int): ByteBuf

  /**
   * Adds the specified [data] to the cache.
   *
   * If the cache is full, the oldest element (at index 0) is evicted, and all other elements are
   * shifted down by one (index 1 becomes 0, etc.). The new element is then added at the last index
   * (`capacity - 1`).
   *
   * @return The index at which the element was added.
   */
  fun add(data: ByteBuf): Int

  /**
   * Returns the index of the last occurrence of the specified [data] in the cache, or -1 if the
   * cache does not contain the element.
   */
  fun indexOf(data: ByteBuf): Int

  /** Removes all elements from the cache and releases their resources. */
  fun clear()

  /**
   * Removes the element at the specified [index] from the cache. Elements with indices greater than
   * [index] are shifted down by one.
   */
  fun remove(index: Int)

  /** The maximum number of elements the cache can hold. */
  val capacity: Int
}
