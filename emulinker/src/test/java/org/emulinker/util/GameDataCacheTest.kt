package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameDataCacheTest {
  private val cache = GameDataCacheImpl(5)

  @Test
  fun `empty cache`() {
    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.toList()).isEmpty()
    assertThat(cache.contains(byteArrayOf(1))).isFalse()
  }

  @Test
  fun `full cache`() {
    repeat(7) { index -> cache.add(byteArrayOf(index.toByte())) }

    assertThat(cache.size).isEqualTo(5)
    assertThat(cache.map { it.single().toInt() }).containsExactly(2, 3, 4, 5, 6)
    for ((index, value) in (2..6).withIndex()) {
      assertThat(cache.contains(byteArrayOf(value.toByte()))).isTrue()
      assertThat(cache.indexOf(byteArrayOf(value.toByte()))).isEqualTo(index)
      assertThat(cache[index]!!.single().toInt()).isEqualTo(value)
    }
  }

  @Test
  fun `clear cache`() {
    repeat(7) { index -> cache.add(byteArrayOf(index.toByte())) }

    cache.clear()

    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.size).isEqualTo(0)
  }
}
