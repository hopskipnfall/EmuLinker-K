package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.util.concurrent.TimeUnit

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

    var checked = 0
    var foundBorrowed = 0
    var foundInCache = 0
    var foundInTemporaryUse = 0

    while (true) {
      incrementIndex()
      checked++
      if (checked >= capacity) {
        logger
          .atWarning()
          .atMostEvery(1, TimeUnit.MINUTES)
          .log(
            "LEAK DETECTED. borrowed= %d cached= %d inTemporaryUse= %d",
            foundBorrowed,
            foundInCache,
            foundInTemporaryUse,
          )
        return allocator()
      }

      val next = pool[index]!!

      val isBorrowed = next.isBorrowed.get()
      val isInCache = next.isInCache.get()
      val inTemporaryUse = next.inTemporaryUse.get()
      if (isBorrowed || isInCache || inTemporaryUse) {
        if (isBorrowed) foundBorrowed++
        if (isInCache) foundInCache++
        if (inTemporaryUse) foundInTemporaryUse++

        continue
      }
      next.isBorrowed.set(true)
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
    v.isBorrowed.set(false)
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
