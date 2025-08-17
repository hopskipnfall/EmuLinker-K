package org.emulinker.util

import java.nio.ByteBuffer
import java.util.Arrays

interface Borrowable {
  var inUse: Boolean
}

class VariableSizeByteArray(initialData: ByteArray = EMPTY_DATA) : Borrowable {
  var bytes = initialData
    private set

  override var inUse: Boolean = false

  var size = initialData.size
    set(newVal) {
      field = newVal
      if (field > bytes.size) {
        bytes = ByteArray(field)
      }
    }

  fun toByteArray(): ByteArray = if (bytes.size == size) bytes else bytes.sliceArray(0 until size)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    if (other !is VariableSizeByteArray) return false
    return Arrays.equals(this.bytes, 0, size, other.bytes, 0, other.size)
  }

  /** Adapted from [java.util.Arrays] */
  override fun hashCode(): Int {
    var result = 1
    for (index in 0 until size) {
      val element = bytes[index]
      result = 31 * result + element
    }

    return result
  }

  fun copyTo(other: VariableSizeByteArray) {
    other.size = size
    System.arraycopy(this.bytes, 0, other.bytes, 0, size)
  }

  fun clone(): VariableSizeByteArray = VariableSizeByteArray(bytes.clone())

  val indices
    get() = 0 until this.size

  operator fun get(index: Int): Byte = bytes[index]

  operator fun set(index: Int, value: Byte) {
    bytes[index] = value
  }

  companion object {
    val EMPTY_DATA = byteArrayOf()

    val pool = ObjectPool<VariableSizeByteArray>(initialSize = 100) { VariableSizeByteArray() }
  }
}

fun ByteBuffer.put(o: VariableSizeByteArray) {
  this.put(o.bytes, 0, o.size)
}

fun ByteBuffer.get(o: VariableSizeByteArray) {
  this.get(o.bytes, 0, o.size)
}
