package org.emulinker.util

import java.nio.ByteBuffer
import java.util.Arrays
import org.emulinker.kaillera.pico.CompiledFlags
import stormpot.Allocator
import stormpot.Expiration
import stormpot.Pool
import stormpot.Poolable
import stormpot.Slot

class VariableSizeByteArray(initialData: ByteArray = EMPTY_DATA, private val slot: Slot? = null) :
  Poolable {
  var bytes = initialData
    private set

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

  override fun release() {
    slot?.release(this)
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

    val pool: Pool<VariableSizeByteArray> =
      Pool.fromInline(
          object : Allocator<VariableSizeByteArray> {
            override fun allocate(p0: Slot): VariableSizeByteArray =
              VariableSizeByteArray(slot = p0)

            override fun deallocate(p0: VariableSizeByteArray) {}
          }
        )
        .setExpiration(Expiration.never())
        .setSize(1_000)
        .build()
  }
}

fun ByteBuffer.put(o: VariableSizeByteArray) {
  this.put(o.bytes, 0, o.size)
}

fun ByteBuffer.get(o: VariableSizeByteArray) {
  this.get(o.bytes, 0, o.size)
}
