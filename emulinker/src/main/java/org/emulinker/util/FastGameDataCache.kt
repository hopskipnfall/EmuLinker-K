package org.emulinker.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/**
 * A [GameDataCache] that keeps a compact copy of each entry in a circular buffer.
 *
 * Every connection has two of these holding the last 256 frames of game data, so they are
 * deliberately cheap for the garbage collector. Entries used to share (and so keep alive) the
 * pooled UDP receive buffer they came from, with a hash key, deque and boxed indices per entry:
 * about ten live objects per entry that every young collection had to copy. Game data is only a few
 * bytes per frame, so an entry is now just a `ByteArray` and an `Int` hash, and lookups scan the
 * hashes (at most [capacity] integer comparisons, no allocation).
 *
 * The cache never retains or releases the buffers it is given, and the buffers returned by [get]
 * are independent of the cache.
 *
 * Not thread safe.
 */
class FastGameDataCache(override val capacity: Int) : GameDataCache {
  private val entries = arrayOfNulls<ByteArray>(capacity)
  private val hashes = IntArray(capacity)

  /**
   * Reused by [indexOf] to read the bytes of the buffer being looked up in one bulk call. Reading
   * them one at a time with `getByte` makes Netty check the buffer's reference count and bounds on
   * every byte, which was ~18% of server CPU in a profile.
   */
  private var scratch = ByteArray(64)

  /** Position in [entries] of the oldest element (logical index 0). */
  private var head = 0

  override var size: Int = 0
    private set

  override fun isEmpty(): Boolean = size == 0

  override operator fun get(index: Int): ByteBuf {
    checkBounds(index)
    // Wrapping does not copy. The caller owns the returned buffer and may release it.
    return Unpooled.wrappedBuffer(entries[slot(index)]!!)
  }

  override fun add(data: ByteBuf): Int {
    if (size == capacity) {
      // Full: evict the oldest element, which makes every other logical index one smaller.
      entries[head] = null
      head = (head + 1) % capacity
      size--
    }
    val bytes = ByteArray(data.readableBytes())
    data.getBytes(data.readerIndex(), bytes)
    val slot = slot(size)
    entries[slot] = bytes
    hashes[slot] = hash(bytes, bytes.size)
    return size++
  }

  override fun indexOf(data: ByteBuf): Int {
    val length = data.readableBytes()
    if (scratch.size < length) scratch = ByteArray(length)
    val bytes = scratch
    data.getBytes(data.readerIndex(), bytes, 0, length)
    val hash = hash(bytes, length)
    // Newest first: the contract is to return the last occurrence.
    for (index in size - 1 downTo 0) {
      val slot = slot(index)
      if (hashes[slot] == hash && matches(entries[slot]!!, bytes, length)) return index
    }
    return -1
  }

  override fun remove(index: Int) {
    checkBounds(index)
    // Shift the newer elements down by one.
    for (i in index until size - 1) {
      val to = slot(i)
      val from = slot(i + 1)
      entries[to] = entries[from]
      hashes[to] = hashes[from]
    }
    entries[slot(size - 1)] = null
    size--
  }

  override fun clear() {
    entries.fill(null)
    head = 0
    size = 0
  }

  override fun contains(element: ByteBuf): Boolean = indexOf(element) != -1

  override fun containsAll(elements: Collection<ByteBuf>): Boolean = elements.all { contains(it) }

  override fun iterator() = iterator<ByteBuf> { repeat(size) { i -> yield(get(i)) } }

  private fun slot(index: Int): Int = (head + index) % capacity

  private fun checkBounds(index: Int) {
    if (index < 0 || index >= size) {
      throw IndexOutOfBoundsException("Index: $index, Size: $size")
    }
  }

  private companion object {
    fun hash(bytes: ByteArray, length: Int): Int {
      var h = 1
      for (i in 0 until length) h = 31 * h + bytes[i]
      return h
    }

    fun matches(entry: ByteArray, bytes: ByteArray, length: Int): Boolean {
      if (entry.size != length) return false
      for (i in 0 until length) if (entry[i] != bytes[i]) return false
      return true
    }
  }
}
