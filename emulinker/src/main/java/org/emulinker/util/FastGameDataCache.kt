package org.emulinker.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil

/** A [GameDataCache] implementation that uses hashing and a circular buffer. */
class FastGameDataCache(override val capacity: Int) : GameDataCache {

  // Circular buffer storage
  private val buffer = arrayOfNulls<ByteBuf>(capacity)

  // Maps Content -> Queue of Absolute Indices
  private class ByteBufKey(val buf: ByteBuf) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ByteBufKey) return false
      return ByteBufUtil.equals(buf, other.buf)
    }

    override fun hashCode(): Int = ByteBufUtil.hashCode(buf)
  }

  private val indexMap = HashMap<ByteBufKey, ArrayDeque<Int>>()

  /** Absolute index of the first element (logical index 0). */
  private var head: Int = 0

  override var size: Int = 0
    private set

  override fun isEmpty(): Boolean = size == 0

  override operator fun get(index: Int): ByteBuf {
    checkBounds(index)
    return buffer[toBufferIndex(head + index)]!!
  }

  override fun add(data: ByteBuf): Int {
    if (size == capacity) {
      // Cache is full: Evict the oldest element (head)
      val headBuf = buffer[toBufferIndex(head)]!!
      val headKey = ByteBufKey(headBuf)
      val indices = indexMap[headKey]

      // Remove the exact absolute index of the head from the map
      indices?.removeFirst()
      if (indices != null && indices.isEmpty()) {
        indexMap.remove(headKey)
      }

      // Clean buffer slot and release the buffer
      buffer[toBufferIndex(head)] = null
      headBuf.release()

      // Advance head: This logically decrements the index of all remaining items by 1
      head++
      size--
    }

    // Add new element at the tail
    val absIndex = head + size
    // Retain the data before storing it
    val retainedData = data.retainedDuplicate()
    buffer[toBufferIndex(absIndex)] = retainedData

    // Update Map
    indexMap.getOrPut(ByteBufKey(retainedData)) { ArrayDeque() }.addLast(absIndex)

    size++
    return size - 1 // Return the new logical index
  }

  override fun indexOf(data: ByteBuf): Int {
    val indices = indexMap[ByteBufKey(data)] ?: return -1
    if (indices.isEmpty()) return -1

    // The last element in the deque is the oldest occurrence (wait, newest added is last?)
    // In add(), we did addLast(absIndex). So last is NEWEST.
    // We usually want to find *any* index, but probably the most recent one?
    // The original implementation used `indices.last()`.
    val lastAbsIndex = indices.last()

    // Convert Absolute Index -> Logical Index
    return lastAbsIndex - head
  }

  override fun remove(index: Int) {
    checkBounds(index)

    // 1. Identify the item to remove
    val absToRemove = head + index
    val dataToRemove = buffer[toBufferIndex(absToRemove)]!!

    // 2. Remove its specific instance from the Map
    val key = ByteBufKey(dataToRemove)
    val indices = indexMap[key]!!
    indices.remove(absToRemove)
    if (indices.isEmpty()) indexMap.remove(key)

    // Release the buffer
    dataToRemove.release()

    // 3. Shift elements physically
    // We shift elements from [index + 1] down to [index]
    for (i in index + 1 until size) {
      val currentAbs = head + i
      val prevAbs = currentAbs - 1

      val dataToMove = buffer[toBufferIndex(currentAbs)]!!

      // Move data in buffer
      buffer[toBufferIndex(prevAbs)] = dataToMove

      // Update Map: The absolute index of this item has changed
      val moveKey = ByteBufKey(dataToMove)
      val entryIndices = indexMap[moveKey]!!

      // Efficiently update the index in the deque
      entryIndices.remove(currentAbs)
      entryIndices.add(prevAbs)
    }

    // 4. Nullify the last slot (now empty)
    buffer[toBufferIndex(head + size - 1)] = null
    size--
  }

  override fun clear() {
    for (i in 0 until capacity) {
      buffer[i]?.release()
      buffer[i] = null
    }
    indexMap.clear()
    head = 0
    size = 0
  }

  override fun contains(element: ByteBuf): Boolean = indexOf(element) != -1

  override fun containsAll(elements: Collection<ByteBuf>): Boolean = elements.all { contains(it) }

  override fun iterator() = iterator<ByteBuf> { repeat(size) { i -> yield(get(i)) } }

  private fun toBufferIndex(absIndex: Int): Int = absIndex % capacity

  private fun checkBounds(index: Int) {
    if (index !in 0 until size) {
      throw IndexOutOfBoundsException("Index: $index, Size: $size")
    }
  }
}
