package org.emulinker.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil

/** A [GameDataCache] implementation that uses hashing and a circular buffer. */
class FastGameDataCache(override val capacity: Int) : GameDataCache {

  // Circular buffer storage
  private val buffer = arrayOfNulls<ByteBuf>(capacity)

  // TODO(nue): Can we make this a value class?
  // Maps Content -> Queue of Absolute Indices
  private class ByteBufKey(val buf: ByteBuf) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ByteBufKey) return false
      // Safety check: One of the buffers might be released (dead key or dead search).
      // We must avoid IllegalReferenceCountException.
      if (buf.refCnt() == 0 || other.buf.refCnt() == 0) {
        return false
      }
      return try {
        ByteBufUtil.equals(buf, other.buf)
      } catch (e: io.netty.util.IllegalReferenceCountException) {
        false
      }
    }

    override fun hashCode(): Int {
      if (buf.refCnt() == 0) return 0
      return try {
        ByteBufUtil.hashCode(buf)
      } catch (e: io.netty.util.IllegalReferenceCountException) {
        0
      }
    }
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

      // Remove the exact absolute index of the head from the map.
      // If 'headKey' represents one of multiple duplicates, 'indices' (ArrayDeque) is sorted by
      // age.
      // 'removeFirst()' correctly removes the oldest instance (current head), leaving newer
      // duplicates intact.
      // This ensures that "if there are two equal elements in the cache, the older one is removed".
      if (indices != null) {
        indices.removeFirst()
        if (indices.isEmpty()) {
          indexMap.remove(headKey)
        } else {
          // KEY SWAP FIX:
          // The current key (headKey) holds a reference to 'headBuf'.
          // 'headBuf' is about to be released. The HashMap still holds 'headKey'.
          // Valid 'indices' remain which verify against 'headKey'.
          // Future lookups will use 'headKey.buf' (which is dead) causing
          // IllegalReferenceCountException.
          // We MUST replace the key with one pointing to a live buffer.
          indexMap.remove(headKey)
          val nextLiveAbsIndex = indices.first()
          val nextLiveBuf = buffer[toBufferIndex(nextLiveAbsIndex)]!!
          indexMap[ByteBufKey(nextLiveBuf)] = indices
        }
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
    if (data.refCnt() == 0) {
      return -1
    }
    val indices = indexMap[ByteBufKey(data)] ?: return -1
    if (indices.isEmpty()) return -1

    // The last element in the deque is the oldest occurrence (wait, newest added is last?)
    // In add(), we did addLast(absIndex). So last is NEWEST.
    // We usually want to find *any* index, but probably the most recent one?
    // The original implementation used `indices.last()`.
    // The GameDataCache interface documentation states "Returns the index of the first occurrence".
    // 'indices' is sorted by insertion order (oldest to newest).
    // So we must use 'first()'.
    val lastAbsIndex = indices.first()

    // Convert Absolute Index -> Logical Index
    return lastAbsIndex - head
  }

  override fun remove(index: Int) {
    checkBounds(index)

    // 1. Identify the item to remove
    val absToRemove = head + index
    val dataToRemove = buffer[toBufferIndex(absToRemove)]!!

    // 2. Remove its specific instance from the Map
    // 2. Remove its specific instance from the Map
    val key = ByteBufKey(dataToRemove)
    val indices = indexMap[key]!!

    // We must remove the index from the list
    indices.remove(absToRemove)

    if (indices.isEmpty()) {
      indexMap.remove(key)
    } else {
      // Safe Key Swap:
      // Regardless of whether 'dataToRemove' was the backing buffer for the map Key,
      // we are about to release 'dataToRemove'.
      // If it WAS the backing buffer, we must swap.
      // If it wasn't, swapping is harmless (just updates key to oldest remaining).
      // To guarantee safety without complex checks, we simply re-key to the new oldest
      // (indices.first()).
      indexMap.remove(key)
      val newFirstAbs = indices.first()
      val newFirstBuf = buffer[toBufferIndex(newFirstAbs)]!!
      indexMap[ByteBufKey(newFirstBuf)] = indices
    }

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
