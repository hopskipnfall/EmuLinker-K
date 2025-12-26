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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class GameDataCacheBenchmark {
  @Setup(Level.Trial) fun setup() {}

  @Setup(Level.Invocation)
  fun setupInvocation() {
    // Reset cache for add-heavy tests if needed, or pre-fill for others
    // For simplicity in this initial pass, we'll manage state per benchmark method logic
  }

  @Benchmark
  fun practicalTest(blackhole: Blackhole) {
    val cache = GameDataCacheImpl(capacity = 256)

    // Takes a sample of actual inputs and cycles them through the cache.
    for (item in inputs) {
      val cacheIndex = cache.indexOf(item)
      if (cacheIndex < 0) cache.add(item)
    }
  }

  private val inputs =
    iterator<VariableSizeByteArray> {
      lateinit var previousLine: String
      for (line in LINES) {
        if (line.startsWith("x")) {
          val times = line.removePrefix("x").toInt()
          repeat(times) { yield(VariableSizeByteArray(previousLine.decodeHex())) }
        } else {
          yield(VariableSizeByteArray(line.decodeHex()))
        }
        previousLine = line
      }
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
