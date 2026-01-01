package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
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
    assertThat(cache.contains(Unpooled.wrappedBuffer(byteArrayOf(1)))).isFalse()
  }

  @Test
  fun `full cache`() {
    repeat(7) { index -> cache.add(Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))) }

    assertThat(cache.size).isEqualTo(5)
    // Validate contents
    val currentContents =
      cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
    assertThat(currentContents).containsExactly(2, 3, 4, 5, 6)

    for ((index, value) in (2..6).withIndex()) {
      val buf = Unpooled.wrappedBuffer(byteArrayOf(value.toByte()))
      assertThat(cache.contains(buf)).isTrue()
      assertThat(cache.indexOf(buf)).isEqualTo(index)

      val item = cache[index]
      assertThat(item.getByte(item.readerIndex()).toInt()).isEqualTo(value)
    }
  }

  @Test
  fun `clear cache`() {
    repeat(7) { index -> cache.add(Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))) }

    cache.clear()

    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.size).isEqualTo(0)
  }

  @Test
  /** The behavior of some clients requires this. */
  fun `add to cache twice retains two copies`() {
    cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))
    cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))

    assertThat(cache.size).isEqualTo(2)
    assertThat(cache.indexOf(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))).isEqualTo(0)
  }

  @Test
  fun iterator() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(entry.toByte())))
    }

    val actualList =
      cache
        .iterator()
        .asSequence()
        .map {
          val arr = ByteArray(it.readableBytes())
          it.getBytes(it.readerIndex(), arr)
          arr.single().toInt()
        }
        .toList()

    assertThat(actualList).containsExactly(1, 4, 3, 4, 5).inOrder()
  }

  @Test
  fun `remove with duplicates`() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(entry.toByte())))
    }

    // Verify initial state
    var actualList =
      cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
    assertThat(actualList).containsExactly(1, 4, 3, 4, 5).inOrder()

    cache.remove(1)

    // Verify after removal
    actualList =
      cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
    assertThat(actualList).containsExactly(1, 3, 4, 5).inOrder()

    // Check indexing
    for (a in byteArrayOf(1, 3, 4, 5).withIndex()) {
      val item = cache[a.index]
      assertThat(item.getByte(item.readerIndex())).isEqualTo(a.value)
    }
  }

  @Test
  fun `add with duplicate at beginning`() {
    for (entry in arrayOf(4, 1, 3, 4, 5)) {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(entry.toByte())))
    }

    cache.add(Unpooled.wrappedBuffer(byteArrayOf(0x09)))

    // Make sure it removed the 4 at the beginning and not the middle.
    val actualList =
      cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
    assertThat(actualList).containsExactly(1, 3, 4, 5, 9).inOrder()
  }
}
