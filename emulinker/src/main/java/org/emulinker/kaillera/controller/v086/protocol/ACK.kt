package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import org.emulinker.util.UnsignedUtil.putUnsignedInt

abstract class ACK : V086Message() {
  abstract val val1: Long
  abstract val val2: Long
  abstract val val3: Long
  abstract val val4: Long

  override val bodyLength = 17

  public override fun writeBodyTo(buffer: ByteBuffer) {
    buffer.put(0x00.toByte())
    buffer.putUnsignedInt(val1)
    buffer.putUnsignedInt(val2)
    buffer.putUnsignedInt(val3)
    buffer.putUnsignedInt(val4)
  }
}
