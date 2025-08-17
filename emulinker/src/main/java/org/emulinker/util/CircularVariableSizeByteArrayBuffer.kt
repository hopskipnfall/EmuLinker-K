package org.emulinker.util

/** NOT THREADSAFE. */
class CircularVariableSizeByteArrayBuffer(
  val size: Int = 0,
  private val allocator: () -> VariableSizeByteArray,
) {
  private val pool = Array(size) { allocator() }
  private var index = 0

  /** Claims an object from the pool. If the pool is empty, a new one is allocated. */
  fun claim(): VariableSizeByteArray {
    while (true) {
      incrementIndex()
      val next = pool[index]
      if (next.inUse) continue
      next.inUse = true
      return next
    }
  }

  private fun incrementIndex() {
    index = (index + 1) % size
  }

  /** Returns an object to the pool. */
  fun recycle(v: VariableSizeByteArray) {
    v.inUse = false
  }
}
