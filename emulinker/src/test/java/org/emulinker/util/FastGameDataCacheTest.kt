package org.emulinker.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.netty.buffer.Unpooled

class FastGameDataCacheTest :
  FunSpec({
    lateinit var cache: FastGameDataCache

    beforeEach {
      cache = FastGameDataCache(5)
      cache.capacity shouldBe 5
    }

    test("cache is initially empty") {
      cache.isEmpty().shouldBeTrue()
      cache.toList().shouldBeEmpty()
      cache.contains(Unpooled.wrappedBuffer(byteArrayOf(1))).shouldBeFalse()
    }

    test("full cache behaves correctly") {
      repeat(7) { index -> cache.add(Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))) }

      cache.size shouldBe 5
      // Validate contents
      val currentContents = cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
      currentContents.shouldContainExactly(2, 3, 4, 5, 6)

      for ((index, value) in (2..6).withIndex()) {
        val buf = Unpooled.wrappedBuffer(byteArrayOf(value.toByte()))
        cache.contains(buf).shouldBeTrue()
        // NOTE: index relative to head? indexOf returns logical index 0..size-1.
        // In the loop above, cache contents are [2, 3, 4, 5, 6].
        // Index 0 -> 2, Index 1 -> 3, etc.
        // value starts at 2. So value 2 is at index 0. value 3 is at index 1.
        // The original test loop did `for ((index, value) in (2..6).withIndex())`
        // (2,0), (3,1), (4,2)... wait. (2..6).withIndex() -> (0, 2), (1, 3), (2, 4)...
        // index is 0, value is 2.
        // cache.indexOf(buf=2) should be 0.
        cache.indexOf(buf) shouldBe index

        val item = cache[index]
        item.getByte(item.readerIndex()).toInt() shouldBe value
      }
    }

    test("clearing cache removes all elements") {
      repeat(7) { index -> cache.add(Unpooled.wrappedBuffer(byteArrayOf(index.toByte()))) }

      cache.clear()

      cache.isEmpty().shouldBeTrue()
      cache.size shouldBe 0
    }

    test("adding to cache twice retains two copies") {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(1.toByte())))

      cache.size shouldBe 2
      // Updated assertion for new "most recent" behavior
      cache.indexOf(Unpooled.wrappedBuffer(byteArrayOf(1.toByte()))) shouldBe 1
    }

    test("iterator returns all elements") {
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

      actualList.shouldContainExactly(1, 4, 3, 4, 5)
    }

    test("removing element with duplicates preserves order") {
      for (entry in arrayOf(1, 4, 3, 4, 5)) {
        cache.add(Unpooled.wrappedBuffer(byteArrayOf(entry.toByte())))
      }

      // Verify initial state
      var actualList = cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
      actualList.shouldContainExactly(1, 4, 3, 4, 5)

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
      actualList = cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
      actualList.shouldContainExactly(1, 3, 4, 5)

      // Check indexing
      for (a in byteArrayOf(1, 3, 4, 5).withIndex()) {
        val item = cache[a.index]
        item.getByte(item.readerIndex()) shouldBe a.value
      }
    }

    test("adding to full cache with duplicate at head evicts correctly") {
      for (entry in arrayOf(4, 1, 3, 4, 5)) {
        cache.add(Unpooled.wrappedBuffer(byteArrayOf(entry.toByte())))
      }

      cache.add(Unpooled.wrappedBuffer(byteArrayOf(0x09)))

      // Make sure it removed the 4 at the beginning and not the middle.
      val actualList = cache.map {
        val arr = ByteArray(it.readableBytes())
        it.getBytes(it.readerIndex(), arr)
        arr.single().toInt()
      }
      actualList.shouldContainExactly(1, 3, 4, 5, 9)
    }

    test("the cache does not retain or release the buffers it is given") {
      val data = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
      data.refCnt() shouldBe 1

      cache.add(data)
      cache.add(data)
      data.refCnt() shouldBe 1

      // Remove the FIRST one (index 0): the duplicate shifts down.
      cache.remove(0)
      cache.contains(data).shouldBeTrue()
      cache.indexOf(data) shouldBe 0

      cache.remove(0)
      cache.isEmpty().shouldBeTrue()
      data.refCnt() shouldBe 1

      data.release()
      data.refCnt() shouldBe 0
    }

    test("entries are independent of the buffer they were copied from") {
      val data1 = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
      cache.add(data1)

      // Consume and release the source, as the sender does once the message has gone out.
      while (data1.isReadable) {
        data1.readByte()
      }
      data1.release()

      // The cached entry is unaffected and starts at its own reader index.
      val cachedItem = cache[0]
      cachedItem.readerIndex() shouldBe 0
      cachedItem.readableBytes() shouldBe 3
      cachedItem.release()

      // Evicting it (this cache holds 5) leaves later lookups of equal content working correctly.
      val data2 = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
      cache.add(data2)
      for (value in 4..7) cache.add(Unpooled.wrappedBuffer(byteArrayOf(value.toByte())))

      val fresh = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))
      cache.indexOf(fresh) shouldBeGreaterThanOrEqual 0
    }

    test("lookup only compares the readable bytes of the buffer") {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(7, 8)))

      // Same content as a slice of a larger buffer.
      val big = Unpooled.wrappedBuffer(byteArrayOf(0, 7, 8, 9))
      big.readerIndex(1)
      big.writerIndex(3)

      cache.indexOf(big) shouldBe 0
    }

    test("indexOf returns last occurrence") {
      val data = Unpooled.wrappedBuffer(byteArrayOf(1))
      // Add twice: [A, A]
      cache.add(data)
      cache.add(data)

      // Should return index 1 (Last occurrence) (Newest)
      cache.indexOf(data) shouldBe 1
    }

    test("indexOf returns most recent duplicate") {
      val data = Unpooled.wrappedBuffer(byteArrayOf(42))

      // Add first time (index 0)
      cache.add(data)

      // Add second time (index 1)
      cache.add(data)

      // Verify indexOf returns 1
      cache.indexOf(data) shouldBe 1

      // Add third time (index 2)
      cache.add(data)

      // Verify indexOf returns 2
      cache.indexOf(data) shouldBe 2

      data.release()
    }

    test("adding on full cache evicts head duplicate") {
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
      smallCache[0] shouldBe b
      smallCache[1] shouldBe a
      smallCache[2] shouldBe c
    }

    test("accessing by index returns distinct objects for equal elements") {
      val data = Unpooled.wrappedBuffer(byteArrayOf(10, 20, 30))

      // Add same content twice
      cache.add(data)
      cache.add(data)

      // Verify we can access both by index
      val first = cache[0]
      val second = cache[1]

      // Content should be equal
      first shouldBe data
      second shouldBe data

      // But they should be distinct objects
      first shouldNotBeSameInstanceAs second

      // Cleanup
      data.release()
    }

    test("eviction removes oldest duplicate") {
      // Create a small cache for testing eviction
      val smallCache = FastGameDataCache(3)
      val a = Unpooled.wrappedBuffer(byteArrayOf(0xAA.toByte()))
      val b = Unpooled.wrappedBuffer(byteArrayOf(0xBB.toByte()))

      // Fill cache: [A1, A2, B]
      smallCache.add(a) // Index 0
      smallCache.add(a) // Index 1
      smallCache.add(b) // Index 2

      // Verify state before eviction
      smallCache[0] shouldBe a
      smallCache[1] shouldBe a
      smallCache[2] shouldBe b

      // Distinct instances check for sanity
      smallCache[0] shouldNotBeSameInstanceAs smallCache[1]

      // Add new element C, should evict oldest A (at index 0)
      val c = Unpooled.wrappedBuffer(byteArrayOf(0xCC.toByte()))
      smallCache.add(c)

      // Cache should now contain [A2, B, C]
      // Indices shift: Old 1 becomes 0, Old 2 becomes 1, New is 2

      smallCache.size shouldBe 3
      smallCache[0] shouldBe a // This is the A that was added second
      smallCache[1] shouldBe b
      smallCache[2] shouldBe c

      // Cleanup
      a.release()
      b.release()
      c.release()
    }

    test("get with index equal to size throws") {
      cache.add(Unpooled.wrappedBuffer(byteArrayOf(1)))
      shouldThrow<IndexOutOfBoundsException> { cache.get(1) }
    }

    test("get with index equal to size on a full cache throws instead of wrapping") {
      repeat(5) { cache.add(Unpooled.wrappedBuffer(byteArrayOf(it.toByte()))) }
      shouldThrow<IndexOutOfBoundsException> { cache.get(5) }
      shouldThrow<IndexOutOfBoundsException> { cache.remove(5) }
    }
  })
