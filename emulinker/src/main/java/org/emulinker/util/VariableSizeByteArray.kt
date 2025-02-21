package org.emulinker.util

import java.nio.ByteBuffer
import java.util.Arrays

class VariableSizeByteArray(initialData: ByteArray = byteArrayOf()) {
  var bytes = initialData
    private set

  var size = initialData.size
    set(newVal) {
      if (newVal > field) {
        bytes = ByteArray(newVal)
      }
      field = newVal
    }

  fun toByteArray(): ByteArray = if (bytes.size == size) bytes else bytes.sliceArray(bytes.indices)

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

  val indices = 0 until this.size

  operator fun get(index: Int): Byte = bytes[index]
}

fun ByteBuffer.put(o: VariableSizeByteArray) {
  this.put(o.bytes, 0, o.bytes.size)
}
