package org.emulinker.util

/** NOT THREADSAFE. */
class CircularVariableSizeByteArrayBuffer(
  private val capacity: Int = 0,
  private val allocator: () -> VariableSizeByteArray,
) {
  private val pool: Array<VariableSizeByteArray?> = arrayOfNulls(capacity)
  private var index = 0

  var size = 0
    private set

  /** Claims an object from the pool. If the pool is empty, a new one is allocated. */
  @Synchronized
  fun borrow(): VariableSizeByteArray {
    // First fill up the pool.
    if (size < capacity) {
      val new = allocator()
      pool[size] = new
      size++
      return new
    }

    while (true) {
      incrementIndex()
      val next = pool[index]!!
      if (next.isBorrowed || next.isInCache || next.inTemporaryUse) continue
      next.isBorrowed = true
      // Effectively clear the data.
      next.size = 0
      return next
    }
  }

  private fun incrementIndex() {
    index = (index + 1) % capacity
  }

  /**
   * Returns an object to the pool.
   *
   * It will not be available for re-use until [VariableSizeByteArray.isInCache] and
   * [VariableSizeByteArray.inTemporaryUse] are also false.
   */
  fun recycle(v: VariableSizeByteArray) {
    v.isBorrowed = false
  }
}
