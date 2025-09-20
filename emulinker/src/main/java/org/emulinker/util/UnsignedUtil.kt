package org.emulinker.util

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer

object UnsignedUtil {
  fun ByteBuffer.getUnsignedByte(): Short = (this.get().toInt() and 0xff).toShort()

  fun ByteBuf.getUnsignedByte(): Short = (this.readByte().toInt() and 0xff).toShort()

  fun ByteBuffer.putUnsignedByte(value: Int) {
    this.put((value and 0xff).toByte())
  }

  fun ByteBuf.putUnsignedByte(value: Int) {
    this.writeByte(value and 0xff)
  }

  // ---------------------------------------------------------------
  fun ByteBuffer.getUnsignedShort(): Int = this.short.toInt() and 0xffff

  fun ByteBuf.getUnsignedShort(): Int = this.readShort().toInt() and 0xffff

  fun ByteBuffer.putUnsignedShort(value: Int) {
    this.putShort((value and 0xffff).toShort())
  }

  @Deprecated("", ReplaceWith("this.writeShortLE(value)"))
  fun ByteBuf.putUnsignedShort(value: Int) {
    this.writeShort((value and 0xffff))
  }

  fun ByteBuffer.getUnsignedShort(position: Int): Int = this.getShort(position).toInt() and 0xffff

  fun ByteBuffer.putUnsignedShort(position: Int, value: Int) {
    this.putShort(position, (value and 0xffff).toShort())
  }

  // ---------------------------------------------------------------
  fun ByteBuffer.getUnsignedInt(): Long = this.int.toLong() and 0xffffffffL

  @Deprecated("", ReplaceWith("this.readIntLE()"))
  fun ByteBuf.getUnsignedInt(): Long = this.readInt().toLong() and 0xffffffffL

  fun ByteBuffer.putUnsignedInt(value: Long) {
    this.putInt((value and 0xffffffffL).toInt())
  }
}
