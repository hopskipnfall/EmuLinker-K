package org.emulinker.util

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class GameDataCacheBenchmark {
  private lateinit var inputs: Iterator<ByteBuf>
  private lateinit var cache: GameDataCache

  private fun buildIterator() =
    iterator<ByteBuf> {
      lateinit var previousLine: String
      while (true) {
        for (line in LINES) {
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

  @Setup(Level.Iteration)
  fun setup() {
    cache = FastGameDataCache(capacity = 256)

    inputs = buildIterator()

    repeat(6_000) {
      val item = inputs.next()

      val cacheIndex = cache.indexOf(item)
      if (cacheIndex < 0) cache.add(item)
    }

    inputs = buildIterator()
  }

  @Benchmark
  fun simulateNewInput(blackhole: Blackhole) {
    val next = inputs.next()
    if (cache.indexOf(next) < 0) cache.add(next)
  }

  private companion object {
    val LINES: List<String> by lazy {
      val inputStream =
        GameDataCacheBenchmark::class.java.getResourceAsStream("/ssb_p1_out.txt")
          ?: throw java.io.FileNotFoundException("ssb_p1_out.txt not found in classpath")
      inputStream.bufferedReader().readLines()
    }
  }
}

/** Turns a hex string into a [ByteArray]. */
private fun String.decodeHex(): ByteArray {
  check(length % 2 == 0) { "Must have an even length" }

  return chunked(2).map { it.lowercase().toInt(16).toByte() }.toByteArray()
}
