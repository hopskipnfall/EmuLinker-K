package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.controller.v086.V086Utils
import org.junit.Test

class VariableSizeByteArrayTest {

  @Test
  fun `basic functions`() {
    val target = VariableSizeByteArray(byteArrayOf(1, 2, 3))

    assertThat(target.size).isEqualTo(3)
    assertThat(target.toByteArray()).isEqualTo(byteArrayOf(1, 2, 3))
    assertThat(target[1]).isEqualTo(2)
    assertThat(target.hashCode()).isEqualTo(target.bytes.contentHashCode())
    assertThat(target.indices).isEqualTo(0 until 3)
  }

  @Test
  fun `ignores shrinked values`() {
    val a = VariableSizeByteArray(byteArrayOf(1, 2, 3))
    val b = VariableSizeByteArray(byteArrayOf(1, 2, 3, 4))
    b.size = 3

    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
    assertThat(a.toByteArray()).isEqualTo(b.toByteArray())
    assertThat(a.indices).isEqualTo(b.indices)
  }

  @Test
  fun `size expands inner array`() {
    val target = VariableSizeByteArray()
    target.size = 10

    assertThat(target.size).isEqualTo(10)
    assertThat(target.bytes).hasLength(10)
  }

  @Test
  fun `ByteBuffer get`() {
    val method1Buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    method1Buffer.position(4)
    val method1Array = ByteArray(method1Buffer.remaining())
    method1Buffer.get(method1Array)
    val method1Target = VariableSizeByteArray(method1Array)

    val method2Buffer = V086Utils.hexStringToByteBuffer(BODY_BYTES)
    method2Buffer.position(4)
    val method2Target = VariableSizeByteArray()
    method2Target.size = method2Buffer.remaining()
    method2Buffer.get(method2Target)

    assertThat(method1Target).isEqualTo(method2Target)
  }

  companion object {
    const val BODY_BYTES =
      "00, 00, 00, 00, 07, 00, 00, 00, 04, 6E, 75, 65, 00, 00, 00, 00, 64, 02, 00, 0D, 01, 6E, 75, 65, 31, 00, 00, 00, 00, 64, 01, 00, 0E, 01, 6E, 75, 65, 32, 00, 00, 00, 00, 64, 00, 00, 12, 04, 6E, 75, 65, 33, 00, 00, 00, 00, 64, 02, 00, C8, 01, 6E, 75, 65, 34, 00, 00, 00, 00, 64, 00, 00, 0C, 01, 6E, 75, 65, 35, 00, 00, 00, 00, 64, 02, 00, 08, 06, 6E, 75, 65, 36, 00, 00, 00, 00, 64, 01, 00, 03, 06, 4D, 79, 20, 52, 4F, 4D, 00, 00, 00, 00, 64, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 00, 32, 2F, 34, 00, 02, 4D, 79, 20, 52, 4F, 4D, 00, 00, 00, 00, 7B, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 32, 00, 32, 2F, 34, 00, 02, 4D, 79, 20, 52, 4F, 4D, 00, 00, 00, 00, 16, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 33, 00, 32, 2F, 34, 00, 01, 4D, 79, 20, 52, 4F, 4D, 00, 00, 00, 00, 05, 4D, 79, 20, 4E, 36, 34, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 6E, 75, 65, 34, 00, 32, 2F, 34, 00, 00"
  }
}
