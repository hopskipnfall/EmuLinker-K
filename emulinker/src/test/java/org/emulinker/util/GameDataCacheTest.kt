package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

abstract class GameDataCacheTest {
  abstract val cache: GameDataCache

  @Before
  fun validate() {
    assertThat(cache.capacity).isEqualTo(5)
  }

  @Test
  fun `empty cache`() {
    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.toList()).isEmpty()
    assertThat(cache.contains(VariableSizeByteArray(byteArrayOf(1)))).isFalse()
  }

  @Test
  fun `full cache`() {
    repeat(7) { index -> cache.add(VariableSizeByteArray(byteArrayOf(index.toByte()))) }

    assertThat(cache.size).isEqualTo(5)
    assertThat(cache.map { it.toByteArray().single().toInt() }).containsExactly(2, 3, 4, 5, 6)
    for ((index, value) in (2..6).withIndex()) {
      assertThat(cache.contains(VariableSizeByteArray(byteArrayOf(value.toByte())))).isTrue()
      assertThat(cache.indexOf(VariableSizeByteArray(byteArrayOf(value.toByte())))).isEqualTo(index)
      assertThat(cache[index].toByteArray().single().toInt()).isEqualTo(value)
    }
  }

  @Test
  fun `clear cache`() {
    repeat(7) { index -> cache.add(VariableSizeByteArray(byteArrayOf(index.toByte()))) }

    cache.clear()

    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.size).isEqualTo(0)
  }

  @Test
  /** The behavior of some clients requires this. */
  fun `add to cache twice retains two copies`() {
    cache.add(VariableSizeByteArray(byteArrayOf(1.toByte())))
    cache.add(VariableSizeByteArray(byteArrayOf(1.toByte())))

    assertThat(cache.size).isEqualTo(2)
    assertThat(cache.indexOf(VariableSizeByteArray(byteArrayOf(1.toByte())))).isEqualTo(1)
  }

  @Test
  fun iterator() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      cache.add(VariableSizeByteArray(byteArrayOf(entry.toByte())))
    }

    assertThat(cache.iterator().asSequence().toList())
      .containsExactlyElementsIn(
        byteArrayOf(1, 4, 3, 4, 5).map { VariableSizeByteArray(byteArrayOf(it)) }
      )
      .inOrder()
  }

  @Test
  fun `remove with duplicates`() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      cache.add(VariableSizeByteArray(byteArrayOf(entry.toByte())))
    }

    assertThat(cache)
      .containsExactlyElementsIn(
        byteArrayOf(1, 4, 3, 4, 5).map { VariableSizeByteArray(byteArrayOf(it)) }
      )
      .inOrder()
    cache.remove(1)

    for (a in byteArrayOf(1, 3, 4, 5).withIndex()) {
      assertThat(cache[a.index].toByteArray().single()).isEqualTo(a.value)
    }
    assertThat(cache)
      .containsExactlyElementsIn(
        byteArrayOf(1, 3, 4, 5).map { VariableSizeByteArray(byteArrayOf(it)) }
      )
      .inOrder()
  }

  @Test
  fun `add with duplicate at beginning`() {
    for (entry in arrayOf(4, 1, 3, 4, 5)) {
      cache.add(VariableSizeByteArray(byteArrayOf(entry.toByte())))
    }

    cache.add(VariableSizeByteArray(byteArrayOf(0x09)))

    // Make sure it removed the 4 at the beginning and not the middle.
    assertThat(cache)
      .containsExactlyElementsIn(
        byteArrayOf(1, 3, 4, 5, 9).map { VariableSizeByteArray(byteArrayOf(it)) }
      )
      .inOrder()
  }
}
