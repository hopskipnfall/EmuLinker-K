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
      return next
    }
  }

  private fun incrementIndex() {
    index = (index + 1) % size
  }

  /** Returns an object to the pool. */
  fun recycle(v: VariableSizeByteArray) {
    v.isBorrowed = false
  }
}
