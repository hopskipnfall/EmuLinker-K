package org.emulinker.util

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readInt
import io.ktor.utils.io.core.readShort
import io.ktor.utils.io.core.readShortLittleEndian
import java.nio.ByteBuffer

object UnsignedUtil {
  fun ByteBuffer.getUnsignedByte(): Short = (this.get().toInt() and 0xff).toShort()
  fun ByteReadPacket.readUnsignedByte(): Short = (this.readByte().toInt() and 0xff).toShort()

  fun ByteBuffer.putUnsignedByte(value: Int) {
    this.put((value and 0xff).toByte())
  }

  fun ByteBuffer.getUnsignedByte(position: Int) = (this[position].toInt() and 0xff).toShort()

  fun ByteBuffer.putUnsignedByte(position: Int, value: Int) {
    this.put(position, (value and 0xff).toByte())
  }

  // ---------------------------------------------------------------
  fun ByteBuffer.getUnsignedShort(): Int = this.short.toInt() and 0xffff

  fun ByteReadPacket.readUnsignedShort(): Int = this.readShort().toInt() and 0xffff
  fun ByteReadPacket.readUnsignedShortLittleEndian(): Int =
    this.readShortLittleEndian().toInt() and 0xffff

  fun ByteBuffer.putUnsignedShort(value: Int) {
    this.putShort((value and 0xffff).toShort())
  }

  fun ByteBuffer.getUnsignedShort(position: Int): Int = this.getShort(position).toInt() and 0xffff

  fun ByteBuffer.putUnsignedShort(position: Int, value: Int) {
    this.putShort(position, (value and 0xffff).toShort())
  }

  // ---------------------------------------------------------------
  fun ByteBuffer.getUnsignedInt(): Long = this.int.toLong() and 0xffffffffL

  fun ByteReadPacket.readUnsignedInt(): Long = this.readInt().toLong() and 0xffffffffL

  fun ByteBuffer.putUnsignedInt(value: Long) {
    this.putInt((value and 0xffffffffL).toInt())
  }

  fun ByteBuffer.getUnsignedInt(position: Int): Long =
    this.getInt(position).toLong() and 0xffffffffL

  fun ByteBuffer.putUnsignedInt(position: Int, value: Long) {
    this.putInt(position, (value and 0xffffffffL).toInt())
  }

  // -----------------
  fun ByteArray.readUnsignedByte(offset: Int): Short = (this[offset].toInt() and 0xFF).toShort()

  fun ByteArray.writeUnsignedByte(s: Short, offset: Int) {
    this[offset] = (s.toInt() and 0xFF).toByte()
  }

  fun ByteArray.readUnsignedShort(offset: Int, littleEndian: Boolean = false): Int =
    if (littleEndian) (this[offset + 1].toInt() and 0xFF shl 8) + (this[offset].toInt() and 0xFF)
    else (this[offset].toInt() and 0xFF shl 8) + (this[offset + 1].toInt() and 0xFF)

  fun writeUnsignedShort(s: Int, bytes: ByteArray?, offset: Int) {
    writeUnsignedShort(s, bytes, offset)
  }

  fun writeUnsignedShort(s: Int, bytes: ByteArray, offset: Int, littleEndian: Boolean) {
    if (littleEndian) {
      bytes[offset] = (s and 0xFF).toByte()
      bytes[offset + 1] = (s ushr 8 and 0xFF).toByte()
    } else {
      bytes[offset] = (s ushr 8 and 0xFF).toByte()
      bytes[offset + 1] = (s and 0xFF).toByte()
    }
  }

  fun ByteArray.readUnsignedInt(offset: Int, littleEndian: Boolean = false): Long {
    val i1: Int = this[offset + 0].toInt() and 0xFF
    val i2: Int = this[offset + 1].toInt() and 0xFF
    val i3: Int = this[offset + 2].toInt() and 0xFF
    val i4: Int = this[offset + 3].toInt() and 0xFF
    return if (littleEndian) {
      ((i4 shl 24) + (i3 shl 16) + (i2 shl 8) + i1).toLong()
    } else {
      ((i1 shl 24) + (i2 shl 16) + (i3 shl 8) + i4).toLong()
    }
  }

  fun ByteArray.writeUnsignedInt(i: Long, offset: Int, littleEndian: Boolean = false) {
    if (littleEndian) {
      this[offset + 0] = (i and 0xFF).toByte()
      this[offset + 1] = (i ushr 8 and 0xFF).toByte()
      this[offset + 2] = (i ushr 16 and 0xFF).toByte()
      this[offset + 3] = (i ushr 24 and 0xFF).toByte()
    } else {
      this[offset + 0] = (i ushr 24 and 0xFF).toByte()
      this[offset + 1] = (i ushr 16 and 0xFF).toByte()
      this[offset + 2] = (i ushr 8 and 0xFF).toByte()
      this[offset + 3] = (i and 0xFF).toByte()
    }
  }
}
