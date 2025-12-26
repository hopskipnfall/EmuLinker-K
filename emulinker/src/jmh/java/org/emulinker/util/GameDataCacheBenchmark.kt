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
  private lateinit var cache: GameDataCacheImpl
  private lateinit var dataItems: List<VariableSizeByteArray>

  @Setup(Level.Trial)
  fun setup() {
    cache = GameDataCacheImpl(capacity = 256)
    dataItems =
      List(256) { i ->
        VariableSizeByteArray().apply {
          size = 24
          this[0] = (i % 256).toByte()
          this[1] = ((i / 256) % 256).toByte()
        }
      }
  }

  @Setup(Level.Invocation)
  fun setupInvocation() {
    // Reset cache for add-heavy tests if needed, or pre-fill for others
    // For simplicity in this initial pass, we'll manage state per benchmark method logic
  }

  @Benchmark
  fun benchmarkAdd(blackhole: Blackhole) {
    // Test filling the cache.
    // Note: Benchmarking 'add' correctly in isolation can be tricky because it changes state.
    // We will create a fresh cache for each batch or just fill it up.
    // Here we'll just add one item to a nearly full cache to measure the eviction cost potentially?
    // Or better, just fill it from empty to full.

    // Let's measure adding a single item.
    // To do this effectively without unbounded growth, we rely on the cache's eviction policy
    // (FIFO).
    // The cache is size 'capacity'.

    val item = dataItems[0] // Reusing same item for simplicity of measurement
    cache.add(item)
  }

  @Benchmark
  fun benchmarkGet(blackhole: Blackhole) {
    // Ensure cache has some data
    if (cache.isEmpty()) {
      dataItems.forEach { cache.add(it) }
    }

    // accessing from middle
    val item = dataItems[cache.size / 2]
    blackhole.consume(cache.indexOf(item).let { if (it >= 0) cache[it] else null })
  }

  @Benchmark
  fun benchmarkRemove(blackhole: Blackhole) {
    // Add if empty
    if (cache.isEmpty()) {
      dataItems.forEach { cache.add(it) }
    }
    // Remove from head (FIFO standard usage)
    cache.remove(0)
  }

  @Benchmark
  fun benchmarkContains(blackhole: Blackhole) {
    // Ensure cache has some data
    if (cache.isEmpty()) {
      dataItems.forEach { cache.add(it) }
    }

    // Check for existing
    val existing = dataItems[cache.size / 2]
    blackhole.consume(cache.contains(existing))

    // Check for non-existing (create a new one not in list)
    val nonExisting =
      VariableSizeByteArray(ByteArray(32)).apply {
        this[0] = 123
        size = 10
      }
    blackhole.consume(cache.contains(nonExisting))
  }
}
