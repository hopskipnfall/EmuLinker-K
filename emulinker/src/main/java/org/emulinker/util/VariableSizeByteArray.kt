package org.emulinker.util

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.util.Arrays

interface Borrowable {
  var isBorrowed: Boolean
  var isInCache: Boolean
  var inTemporaryUse: Boolean
}

class VariableSizeByteArray(initialData: ByteArray = EMPTY_DATA) : Borrowable {
  var bytes = initialData
    private set

  override var isBorrowed: Boolean = false
  override var isInCache: Boolean = false
  override var inTemporaryUse: Boolean = false

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

  override fun toString(): String = bytes.toHexString()

  fun copyTo(other: VariableSizeByteArray) {
    other.size = size
    System.arraycopy(this.bytes, 0, other.bytes, 0, size)
  }

  /** Sets a range of values to zero. */
  fun setZeroesForRange(fromIndex: Int, untilIndexExclusive: Int) {
    bytes.fill(0x00, fromIndex, untilIndexExclusive)
  }

  fun nativeCopyDataFrom(
    copyFrom: ByteArray,
    writeAtIndex: Int,
    readStartIndex: Int,
    readLength: Int,
  ) {
    require(writeAtIndex + readLength <= size) { "Write length out of bounds!" }
    System.arraycopy(
      /* src= */ copyFrom,
      /* srcPos= */ readStartIndex,
      /* dest= */ bytes,
      /* destPos= */ writeAtIndex,
      /* length= */ readLength,
    )
  }

  fun clone(): VariableSizeByteArray = VariableSizeByteArray(bytes.clone())

  val indices: IntRange
    get() = 0 until this.size

  operator fun get(index: Int): Byte = bytes[index]

  operator fun set(index: Int, value: Byte) {
    bytes[index] = value
  }

  companion object {
    private val EMPTY_DATA = byteArrayOf()
  }
}

fun ByteBuffer.put(o: VariableSizeByteArray) {
  this.put(o.bytes, 0, o.size)
}

fun ByteBuffer.get(o: VariableSizeByteArray) {
  this.get(o.bytes, 0, o.size)
}

fun ByteBuf.get(o: VariableSizeByteArray) {
  this.readBytes(/* dst= */ o.bytes, /* dstIndex= */ 0, /* length= */ o.size)
}
