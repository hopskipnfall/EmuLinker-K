package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import kotlin.random.Random
import org.junit.Test

/** Compares [FastGameDataCache] with a trivial list-based model under random operations. */
class FastGameDataCacheModelTest {
  @Test
  fun behavesLikeAPlainList() {
    val random = Random(42)
    repeat(40) {
      val capacity = random.nextInt(1, 12)
      val cache = FastGameDataCache(capacity)
      val model = ArrayList<List<Byte>>()
      // A small alphabet makes duplicates (the interesting case) common.
      fun randomData() = List(random.nextInt(0, 4)) { random.nextInt(3).toByte() }

      repeat(500) {
        when (random.nextInt(4)) {
          0,
          1 -> {
            val data = randomData()
            if (model.size == capacity) model.removeAt(0)
            model.add(data)
            val index = cache.add(Unpooled.wrappedBuffer(data.toByteArray()))
            assertThat(index).isEqualTo(model.size - 1)
          }
          2 -> {
            val data = randomData()
            val expected = model.lastIndexOf(data)
            assertThat(cache.indexOf(Unpooled.wrappedBuffer(data.toByteArray())))
              .isEqualTo(expected)
          }
          else ->
            if (model.isNotEmpty()) {
              val index = random.nextInt(model.size)
              model.removeAt(index)
              cache.remove(index)
            }
        }
        assertThat(cache.size).isEqualTo(model.size)
        for (i in model.indices) {
          val actual = cache[i]
          assertThat(ByteArray(actual.readableBytes()).also { actual.getBytes(0, it) }.toList())
            .isEqualTo(model[i])
        }
      }
    }
  }
}
