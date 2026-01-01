package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import org.junit.Test

class FastGameDataCacheTest : GameDataCacheTest() {
  override val cache = FastGameDataCache(5)

  @Test
  fun testReferenceCountingWithDuplicates() {
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
    // This is the critical step: if the Key Swap Fix works, the map key (which might correspond
    // to the buffer we just removed) should be swapped to the remaining buffer.
    cache.remove(0)

    // RefCount should drop to 2 (1 original + 1 remaining in cache)
    assertThat(data.refCnt()).isEqualTo(2)

    // VERIFY USE-AFTER-FREE FIX:
    // Calling indexOf / contains relies on the Map Key.
    // If the Key pointed to the released buffer and wasn't swapped, this would throw
    // IllegalReferenceCountException.
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
  fun testFastGameDataCacheReproduction() {
    // Simulate flow:
    // 1. Create Data
    val data1 = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    // 2. Add to Cache (creates retainedDuplicate)
    cache.add(data1)

    // 3. Simulate Send: Consume the data (move readerIndex)
    // This happens in GameData.writeTo
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

    // data1 should be released by cache eviction?
    // Wait. cache[0] (stored duplicate) is released.
    // data1 (original) refCnt should drop.
    assertThat(data1.refCnt()).isEqualTo(1) // Just data1 ref left.

    // 7. Verify map key is swapped to data2?
    // Map should contain entry for [1, 2, 3] pointing to index...
    // cache[0] evicted. cache[1] is data2.
    // Index of data2 is...
    // head was 0. evicted. head=1.
    // data2 added at abs index 1.
    // cache.indexOf(freshData) should find it.
    val fresh = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
    assertThat(cache.indexOf(fresh)).isAtLeast(0)

    data1.release()
    data2.release()
  }

  @Test
  fun testIndexOfFirstOccurrence() {
    val data = Unpooled.wrappedBuffer(byteArrayOf(1))
    // Add twice: [A, A]
    cache.add(data)
    cache.add(data)

    // Should return index 0 (First occurrence) (Oldest)
    assertThat(cache.indexOf(data)).isEqualTo(0)
  }

  @Test
  fun testAddOnFullCacheEvictsHeadDuplicate() {
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
}
