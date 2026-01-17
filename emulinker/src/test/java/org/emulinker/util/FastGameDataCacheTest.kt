package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import org.junit.Before
import org.junit.Test

class FastGameDataCacheTest {
  private val cache = FastGameDataCache(5)

  @Before
  fun `capacity is correct`() {
    assertThat(cache.capacity).isEqualTo(5)
  }

  @Test
  fun `cache is initially empty`() {
    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.toList()).isEmpty()
    assertThat(cache.contains(Unpooled.wrappedBuffer(byteArrayOf(1)))).isFalse()
  }

  @Test
  fun `full cache behaves correctly`() {
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
      // NOTE: index relative to head? indexOf returns logical index 0..size-1.
      // In the loop above, cache contents are [2, 3, 4, 5, 6].
      // Index 0 -> 2, Index 1 -> 3, etc.
      // value starts at 2. So value 2 is at index 0. value 3 is at index 1.
      // The original test loop did `for ((index, value) in (2..6).withIndex())`
      // (2,0), (3,1), (4,2)... wait. (2..6).withIndex() -> (0, 2), (1, 3), (2, 4)...
      // index is 0, value is 2.
      // cache.indexOf(buf=2) should be 0.
      assertThat(cache.indexOf(buf)).isEqualTo(index)

      val item = cache[index]
      assertThat(item.getByte(item.readerIndex()).toInt()).isEqualTo(value)
    }
  }

  @Test
  fun `clearing cache removes all elements`() {
    repeat(7) { index -> cache.add(Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))) }

    cache.clear()

    assertThat(cache.isEmpty()).isTrue()
    assertThat(cache.size).isEqualTo(0)
  }

  @Test
  fun `adding to cache twice retains two copies`() {
    cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))
    cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))

    assertThat(cache.size).isEqualTo(2)
    // Updated assertion for new "most recent" behavior
    assertThat(cache.indexOf(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))).isEqualTo(1)
  }

  @Test
  fun `iterator returns all elements`() {
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
  fun `removing element with duplicates preserves order`() {
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

    // Remove element at index 0 (which is 1)
    // Note: The original test did `cache.remove(1)`. Since it was an abstract test,
    // it likely relied on correct remove behavior.
    // If it meant remove value 1, FastGameDataCache doesn't have remove(object).
    // It has remove(index).
    // Let's check original GameDataCacheTest.
    // "cache.remove(1)".
    // If cache is [1, 4, 3, 4, 5]. Index 1 is 4.
    // Result expected: [1, 3, 4, 5].
    // So it removed index 1.

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
  fun `adding to full cache with duplicate at head evicts correctly`() {
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

  @Test
  fun `reference counting handles duplicates correctly`() {
    val data = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    // Initial RefCount = 1
    assertThat(data.refCnt()).isEqualTo(1)

    // Add first time: RefCount should be 2 (1 original + 1 retained by cache)
    cache.add(data)
    assertThat(data.refCnt()).isEqualTo(2)

    // Add duplicate: RefCount should be 3 (1 original + 2 retained by cache)
    cache.add(data)
    assertThat(data.refCnt()).isEqualTo(3)

    // Remove the FIRST one (index 0).
    cache.remove(0)

    // RefCount should drop to 2 (1 original + 1 remaining in cache)
    assertThat(data.refCnt()).isEqualTo(2)

    assertThat(cache.contains(data)).isTrue()
    assertThat(cache.indexOf(data)).isEqualTo(0) // It's now at index 0 because we shifted

    // Remove the remaining one
    cache.remove(0)

    // RefCount should drop to 1 (1 original)
    assertThat(data.refCnt()).isEqualTo(1)
    assertThat(cache.isEmpty()).isTrue()

    // Release the original reference
    data.release()
    assertThat(data.refCnt()).isEqualTo(0)
  }

  @Test
  fun `reproduction scenario for use-after-free`() {
    // Simulate flow:
    // 1. Create Data
    val data1 = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    // 2. Add to Cache (creates retainedDuplicate)
    cache.add(data1)

    // 3. Simulate Send: Consume the data (move readerIndex)
    while (data1.isReadable) {
      data1.readByte()
    }
    assertThat(data1.readerIndex()).isEqualTo(3)

    // 4. Verify Cache entry is unaffected (independent index)
    val cachedItem = cache[0]
    assertThat(cachedItem.readerIndex()).isEqualTo(0)
    assertThat(cachedItem.refCnt()).isEqualTo(2) // 1 original + 1 cache

    // 5. Add duplicate (fresh buffer, same content)
    val data2 = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    cache.add(data2)

    // 6. Evict first item (requires filling cache)
    val d3 = Unpooled.wrappedBuffer(byteArrayOf(4))
    val d4 = Unpooled.wrappedBuffer(byteArrayOf(5))
    val d5 = Unpooled.wrappedBuffer(byteArrayOf(6))
    val d6 = Unpooled.wrappedBuffer(byteArrayOf(7))
    cache.add(d3)
    cache.add(d4)
    cache.add(d5)
    cache.add(d6) // This should evict 0 (data1)

    assertThat(data1.refCnt()).isEqualTo(1) // Just data1 ref left.

    val fresh = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    assertThat(cache.indexOf(fresh)).isAtLeast(0)

    data1.release()
    data2.release()
  }

  @Test
  fun `indexOf returns last occurrence`() {
    val data = Unpooled.wrappedBuffer(byteArrayOf(1))
    // Add twice: [A, A]
    cache.add(data)
    cache.add(data)

    // Should return index 1 (Last occurrence) (Newest)
    assertThat(cache.indexOf(data)).isEqualTo(1)
  }

  @Test
  fun `indexOf returns most recent duplicate`() {
    val data = Unpooled.wrappedBuffer(byteArrayOf(42))

    // Add first time (index 0)
    cache.add(data)

    // Add second time (index 1)
    cache.add(data)

    // Verify indexOf returns 1
    assertThat(cache.indexOf(data)).isEqualTo(1)

    // Add third time (index 2)
    cache.add(data)

    // Verify indexOf returns 2
    assertThat(cache.indexOf(data)).isEqualTo(2)

    data.release()
  }

  @Test
  fun `adding on full cache evicts head duplicate`() {
    // Re-create cache with size 3
    val smallCache = FastGameDataCache(3)
    val a = Unpooled.wrappedBuffer(byteArrayOf(1))
    val b = Unpooled.wrappedBuffer(byteArrayOf(2))

    // [A, B, A]
    smallCache.add(a)
    smallCache.add(b)
    smallCache.add(a)

    // Add C (Full)
    val c = Unpooled.wrappedBuffer(byteArrayOf(3))
    smallCache.add(c)

    // Expect: Head (A at 0) evicted. Result [B, A, C]
    assertThat(smallCache[0]).isEqualTo(b)
    assertThat(smallCache[1]).isEqualTo(a)
    assertThat(smallCache[2]).isEqualTo(c)
  }

  @Test
  fun `accessing by index returns distinct objects for equal elements`() {
    val data = Unpooled.wrappedBuffer(byteArrayOf(10, 20, 30))

    // Add same content twice
    cache.add(data)
    cache.add(data)

    // Verify we can access both by index
    val first = cache[0]
    val second = cache[1]

    // Content should be equal
    assertThat(first).isEqualTo(data)
    assertThat(second).isEqualTo(data)

    // But they should be distinct objects (retained duplicates)
    assertThat(first).isNotSameInstanceAs(second)

    // Cleanup
    data.release()
  }

  @Test
  fun `eviction removes oldest duplicate`() {
    // Create a small cache for testing eviction
    val smallCache = FastGameDataCache(3)
    val a = Unpooled.wrappedBuffer(byteArrayOf(0xAA.toByte()))
    val b = Unpooled.wrappedBuffer(byteArrayOf(0xBB.toByte()))

    // Fill cache: [A1, A2, B]
    smallCache.add(a) // Index 0
    smallCache.add(a) // Index 1
    smallCache.add(b) // Index 2

    // Verify state before eviction
    assertThat(smallCache[0]).isEqualTo(a)
    assertThat(smallCache[1]).isEqualTo(a)
    assertThat(smallCache[2]).isEqualTo(b)

    // Distinct instances check for sanity
    assertThat(smallCache[0]).isNotSameInstanceAs(smallCache[1])

    // Add new element C, should evict oldest A (at index 0)
    val c = Unpooled.wrappedBuffer(byteArrayOf(0xCC.toByte()))
    smallCache.add(c)

    // Cache should now contain [A2, B, C]
    // Indices shift: Old 1 becomes 0, Old 2 becomes 1, New is 2

    assertThat(smallCache.size).isEqualTo(3)
    assertThat(smallCache[0]).isEqualTo(a) // This is the A that was added second
    assertThat(smallCache[1]).isEqualTo(b)
    assertThat(smallCache[2]).isEqualTo(c)

    // Cleanup
    a.release()
    b.release()
    c.release()
  }
}
