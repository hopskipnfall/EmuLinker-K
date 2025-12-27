package org.emulinker.util

/** A [GameDataCache] implementation that uses hashing and a circular buffer. */
class FastGameDataCache(private val capacity: Int) : GameDataCache {

  // Circular buffer storage
  private val buffer = arrayOfNulls<VariableSizeByteArray>(capacity)

  // Maps Content -> Queue of Absolute Indices
  private val indexMap = HashMap<VariableSizeByteArray, ArrayDeque<Int>>()

  /** Absolute index of the first element (logical index 0). */
  private var head: Int = 0

  override var size: Int = 0
    private set

  override fun isEmpty(): Boolean = size == 0

  override operator fun get(index: Int): VariableSizeByteArray {
    checkBounds(index)
    return buffer[toBufferIndex(head + index)]!!
  }

  override fun add(data: VariableSizeByteArray): Int {
    if (size == capacity) {
      // Cache is full: Evict the oldest element (head)
      val headKey = buffer[toBufferIndex(head)]!!
      val indices = indexMap[headKey]

      // Remove the exact absolute index of the head from the map
      indices?.removeFirst()
      if (indices != null && indices.isEmpty()) {
        indexMap.remove(headKey)
      }

      // Clean buffer slot (optional, but good for GC)
      buffer[toBufferIndex(head)] = null

      // Advance head: This logically decrements the index of all remaining items by 1
      head++
      size--
    }

    // Add new element at the tail
    val absIndex = head + size
    buffer[toBufferIndex(absIndex)] = data

    // Update Map: O(1)
    indexMap.getOrPut(data) { ArrayDeque() }.addLast(absIndex)

    size++
    return size - 1 // Return the new logical index
  }

  override fun indexOf(data: VariableSizeByteArray): Int {
    val indices = indexMap[data] ?: return -1
    if (indices.isEmpty()) return -1

    // The first element in the deque is the oldest occurrence
    val firstAbsIndex = indices.last()

    // Convert Absolute Index -> Logical Index
    return firstAbsIndex - head
  }

  override fun remove(index: Int) {
    checkBounds(index)

    // 1. Identify the item to remove
    val absToRemove = head + index
    val dataToRemove = buffer[toBufferIndex(absToRemove)]!!

    // 2. Remove its specific instance from the Map
    // We must remove the specific absolute index, not just any instance
    val indices = indexMap[dataToRemove]!!
    indices.remove(absToRemove)
    if (indices.isEmpty()) indexMap.remove(dataToRemove)

    // 3. Shift elements physically
    // Because we are removing from the middle, we cannot use ring buffer magic alone.
    // We must preserve the order of subsequent items.
    // We shift elements from [index + 1] down to [index]
    for (i in index + 1 until size) {
      val currentAbs = head + i
      val prevAbs = currentAbs - 1 // The 'hole' we are filling

      val dataToMove = buffer[toBufferIndex(currentAbs)]!!

      // Move data in buffer
      buffer[toBufferIndex(prevAbs)] = dataToMove

      // Update Map: The absolute index of this item has changed
      val key = dataToMove
      val entryIndices = indexMap[key]!!

      // Efficiently update the index in the deque
      // Since we iterate linearly, order is preserved naturally
      entryIndices.remove(currentAbs)
      entryIndices.add(prevAbs)
      // Note: We use add() because we are conceptually appending the 'new' location
      // However, since we scan strictly left-to-right, the relative order in the deque remains
      // correct.
    }

    // 4. Nullify the last slot (now empty)
    buffer[toBufferIndex(head + size - 1)] = null
    size--
  }

  override fun clear() {
    buffer.fill(null)
    indexMap.clear()
    head = 0
    size = 0
  }

  override fun contains(element: VariableSizeByteArray): Boolean = indexOf(element) != -1

  override fun containsAll(elements: Collection<VariableSizeByteArray>): Boolean =
    elements.all { contains(it) }

  override fun iterator() = iterator<VariableSizeByteArray> { repeat(size) { i -> yield(get(i)) } }

  private fun toBufferIndex(absIndex: Int): Int = absIndex % capacity

  private fun checkBounds(index: Int) {
    if (index !in 0 until size) {
      throw IndexOutOfBoundsException("Index: $index, Size: $size")
    }
  }
}
