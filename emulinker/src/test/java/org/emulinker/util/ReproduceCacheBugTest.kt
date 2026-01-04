package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.FileNotFoundException
import org.junit.Test

class ReproduceCacheBugTest {

  @Test
  fun `cache consistency test with real data`() {
    val cache = FastGameDataCache(256)
    val iterator = buildIterator()

    var count = 0
    while (count < 2000 && iterator.hasNext()) { // Run enough to loop buffer multiple times
      val data = iterator.next()

      val index = cache.indexOf(data)
      if (index != -1) {
        // Cache Hit: Verify data matches
        val cachedMatches =
          try {
            val cached = cache[index]
            cached == data // ByteBuf.equals checks content
          } catch (e: Exception) {
            throw RuntimeException("Failed to access cache at index $index", e)
          }

        assertThat(cachedMatches).isTrue()
      }

      cache.add(data)
      data.release() // Iterator creates fresh buffers, release them
      count++
    }
  }

  private fun buildIterator() =
    iterator<ByteBuf> {
      val lines =
        ReproduceCacheBugTest::class
          .java
          .getResourceAsStream("/ssb_p1_out.txt")
          ?.bufferedReader()
          ?.readLines() ?: throw FileNotFoundException("ssb_p1_out.txt not found")

      var previousLine: String = ""
      while (true) {
        for (line in lines) {
          if (line.startsWith("x")) {
            val times = line.removePrefix("x").toInt()
            repeat(times) { yield(Unpooled.wrappedBuffer(previousLine.decodeHex())) }
          } else {
            yield(Unpooled.wrappedBuffer(line.decodeHex()))
          }
          previousLine = line
        }
      }
    }

  private fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2).map { it.lowercase().toInt(16).toByte() }.toByteArray()
  }
}
