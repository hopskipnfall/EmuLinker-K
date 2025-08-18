package org.emulinker.util

/** NOT THREADSAFE. */
class CircularVariableSizeByteArrayBuffer(
  val size: Int = 0,
  private val allocator: () -> VariableSizeByteArray,
) {
  private val pool = Array(size) { allocator() }
  private var index = 0

  /** Claims an object from the pool. If the pool is empty, a new one is allocated. */
  fun borrow(): VariableSizeByteArray {
    while (true) {
      incrementIndex()
      val next = pool[index]
      if (next.isBorrowed || next.isInCache || next.inTemporaryUse) continue
      next.isBorrowed = true
      // Effectively clear the data.
      next.size = 0
      return next
    }
  }

  private fun incrementIndex() {
    index = (index + 1) % size
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
