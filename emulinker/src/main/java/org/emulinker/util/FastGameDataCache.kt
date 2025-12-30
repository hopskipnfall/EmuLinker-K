package org.emulinker.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil

/** A [GameDataCache] implementation that uses hashing and a circular buffer. */
class FastGameDataCache(override val capacity: Int) : GameDataCache {

  // Circular buffer storage
  private val buffer = arrayOfNulls<ByteBuf>(capacity)

  // Maps Content -> Queue of Absolute Indices
  // We cannot use ByteBuf as a Map key directly because its hashCode/equals are based on content AND identity sometimes?
  // Actually ByteBufUtil.hashCode(buf) and ByteBufUtil.equals(buf1, buf2) are what we need.
  // But standard HashMap uses Object.hashCode/equals.
  // So we need a wrapper key or similar.
  // However, since we are doing a simpler refactor, we can scan or just rely on a custom map if needed.
  // Or simpler: Wrap ByteBuf for the Map key.
  private val indexMap = HashMap<ByteBufKey, ArrayDeque<Int>>()

  /** Absolute index of the first element (logical index 0). */
  private var head: Int = 0

  override var size: Int = 0
    private set

  override fun isEmpty(): Boolean = size == 0

  override operator fun get(index: Int): ByteBuf {
    checkBounds(index)
    val buf = buffer[toBufferIndex(head + index)]!!
    // Return a retained slice to protect the cached data?
    // Start with retain(). The caller must release it.
    // Actually, usually users of caches just want to peek.
    // But since GameData takes ownership, we MUST retain it.
    return buf.retainedSlice()
  }

  override fun iterator() = iterator<ByteBuf> { repeat(size) { i -> yield(get(i)) } }

  // We change the signature to accept ByteBuf
  // Note: The interface GameDataCache is generic-less in code but we need to update it too.
  // The original signature was add(data: VariableSizeByteArray).
  // I will check the interface file next. For now assuming I update existing code.
  override fun add(data: ByteBuf): Int {
    val storedData = data.copy()

    if (size == capacity) {
      val headBuf = buffer[toBufferIndex(head)]!!
      val headKey = ByteBufKey(headBuf)
      val indices = indexMap[headKey]

      if (indices != null) {
        // Remove the old entry completely to update key if needed
        indexMap.remove(headKey)
        indices.removeFirst() // Remove the head index

        if (indices.isNotEmpty()) {
          // Re-insert with a new key from a survivor
          val survivorAbs = indices.first()
          val survivorBuf = buffer[toBufferIndex(survivorAbs)]!!
          indexMap[ByteBufKey(survivorBuf)] = indices
        }
      }

      headBuf.release()
      buffer[toBufferIndex(head)] = null

      head++
      size--
    }

    val absIndex = head + size
    buffer[toBufferIndex(absIndex)] = storedData

    // Update Map
    // We might have an existing entry. If so, usage is fine (key is alive).
    indexMap.getOrPut(ByteBufKey(storedData)) { ArrayDeque() }.addLast(absIndex)

    size++
    return size - 1
  }

  override fun indexOf(data: ByteBuf): Int {
    val indices = indexMap[ByteBufKey(data)] ?: return -1
    if (indices.isEmpty()) return -1
    val lastAbsIndex = indices.last()
    return lastAbsIndex - head
  }

  override fun remove(index: Int) {
    checkBounds(index)

    val absToRemove = head + index
    val dataToRemove = buffer[toBufferIndex(absToRemove)]!!
    val key = ByteBufKey(dataToRemove)
    val indices = indexMap[key]!!

    // Remove entry to ensure no dead key remains
    indexMap.remove(key)
    indices.remove(absToRemove)

    // Release BEFORE re-keying? No, survivor must be alive. dataToRemove is released.
    // We pick survivor from indices.
    if (indices.isNotEmpty()) {
      val survivorAbs = indices.first()
      val survivorBuf = buffer[toBufferIndex(survivorAbs)]!!
      indexMap[ByteBufKey(survivorBuf)] = indices
    }

    dataToRemove.release()

    for (i in index + 1 until size) {
      val currentAbs = head + i
      val prevAbs = currentAbs - 1

      val dataToMove = buffer[toBufferIndex(currentAbs)]!!
      buffer[toBufferIndex(prevAbs)] = dataToMove

      val moveKey = ByteBufKey(dataToMove)
      val entryIndices = indexMap[moveKey]!!

      entryIndices.remove(currentAbs)
      entryIndices.add(prevAbs)
    }

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

  // Helper class for Map Key
  private class ByteBufKey(val buffer: ByteBuf) {
    override fun equals(other: Any?): Boolean {
      return other is ByteBufKey && ByteBufUtil.equals(buffer, other.buffer)
    }

    override fun hashCode(): Int {
      return ByteBufUtil.hashCode(buffer)
    }
  }

  // To implement interface methods if they depend on VariableSizeByteArray,
  // I need to update the interface `GameDataCache` first.
  // For now I'm implementing what matches the logic.

  // NOTE: This file replacement assumes I will update the interface right after or it might break compilation if I kept overrides.
  // Since I am replacing the content, I will remove overrides that don't match yet or adapt them.
  // "override fun add(data: VariableSizeByteArray)" -> changed to ByteBuf.

  private fun toBufferIndex(absIndex: Int): Int = absIndex % capacity

  private fun checkBounds(index: Int) {
    if (index !in 0 until size) {
      throw IndexOutOfBoundsException("Index: $index, Size: $size")
    }
  }
}
