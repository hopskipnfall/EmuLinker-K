package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBuf
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
    // GameDataCache no longer implements Collection so toList() is gone unless we cast or iterate.
    // We added iterator() to interface so it should work if we treat it as Sequence or Loop.
    // But GameDataCache extends Collection? No we removed it.
    // So iterator() exists.
    assertThat(cache.iterator().asSequence().toList()).isEmpty()
    val buf = Unpooled.wrappedBuffer(byteArrayOf(1))
    assertThat(cache.indexOf(buf)).isEqualTo(-1)
    buf.release()
  }

  @Test
  fun `full cache`() {
    repeat(7) { index ->
      val buf = Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))
      cache.add(buf)
      buf.release() // add retains copy
    }

    assertThat(cache.size).isEqualTo(5)

    // Check content
    val content = (0 until cache.size).map { cache[it].getByte(0).toInt() }
    assertThat(content).containsExactly(2, 3, 4, 5, 6)

    // Check individual items
    for ((index, value) in (2..6).withIndex()) {
      val lookupBuf = Unpooled.wrappedBuffer(byteArrayOf(value.toByte()))
      assertThat(cache.indexOf(lookupBuf)).isEqualTo(index)
      assertThat(cache[index].getByte(0).toInt()).isEqualTo(value)
      lookupBuf.release()
    }
  }

  @Test
  fun `clear cache`() {
    repeat(7) { index ->
      val buf = Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))
      cache.add(buf)
      buf.release()
    }

    cache.clear()

    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.size).isEqualTo(0)
  }

  @Test
          /** The behavior of some clients requires this. */
  fun `add to cache twice retains two copies`() {
    val buf1 = Unpooled.wrappedBuffer(byteArrayOf(1.toByte()))
    cache.add(buf1)
    buf1.release()

    val buf2 = Unpooled.wrappedBuffer(byteArrayOf(1.toByte()))
    cache.add(buf2)
    buf2.release()

    assertThat(cache.size).isEqualTo(2)

    val lookup = Unpooled.wrappedBuffer(byteArrayOf(1.toByte()))
    // indexOf returns the last occurrence index?
    // FastGameDataCache: last in deque -> newest added.
    // We added index 0 then index 1.
    // last() -> 1.
    assertThat(cache.indexOf(lookup)).isEqualTo(1)
    lookup.release()
  }

  @Test
  fun iterator() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      val buf = Unpooled.wrappedBuffer(byteArrayOf(entry.toByte()))
      cache.add(buf)
      buf.release()
    }

    val list = cache.iterator().asSequence().toList()
    assertThat(list.map { it.getByte(0).toInt() })
      .containsExactlyElementsIn(arrayOf(1, 4, 3, 4, 5))
      .inOrder()
  }

  @Test
  fun `remove with duplicates`() {
    for (entry in arrayOf(1, 4, 3, 4, 5)) {
      val buf = Unpooled.wrappedBuffer(byteArrayOf(entry.toByte()))
      cache.add(buf)
      buf.release()
    }

    // cache: [1, 4, 3, 4, 5]
    // remove index 1 (value 4)
    cache.remove(1)

    // Expected: [1, 3, 4, 5]
    val list = cache.iterator().asSequence().toList()
    assertThat(list.map { it.getByte(0).toInt() })
      .containsExactlyElementsIn(list.map { it.getByte(0).toInt() }) // self check?
    // Check specific values
    assertThat(list.map { it.getByte(0).toInt() })
      .containsExactly(1, 3, 4, 5)
      .inOrder()
  }

  @Test
  fun `add with duplicate at beginning`() {
    for (entry in arrayOf(4, 1, 3, 4, 5)) {
      val buf = Unpooled.wrappedBuffer(byteArrayOf(entry.toByte()))
      cache.add(buf)
      buf.release()
    }

    val buf9 = Unpooled.wrappedBuffer(byteArrayOf(0x09))
    cache.add(buf9)
    buf9.release()

    // Cache size 5. Added 5 items: [4, 1, 3, 4, 5]
    // Added 6th item (9).
    // Evict head (4).
    // Remaining: [1, 3, 4, 5, 9] (logical order)

    val list = cache.iterator().asSequence().toList()
    assertThat(list.map { it.getByte(0).toInt() })
      .containsExactly(1, 3, 4, 5, 9)
      .inOrder()
  }
}
