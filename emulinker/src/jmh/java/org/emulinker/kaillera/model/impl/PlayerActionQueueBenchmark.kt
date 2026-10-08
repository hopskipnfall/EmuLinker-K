package org.emulinker.kaillera.model.impl

import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

/**
 * Compares the current [PlayerActionQueue] (ring buffer) against the previous implementation
 * (`CompositeByteBuf`, kept below as [LegacyCompositeActionQueue]) for one simulated game frame:
 * every player's input is added once, then every player reads every queue's actions once. This is
 * the per-frame fan-out cost in `KailleraGame.maybeSendData`.
 *
 * Run with: `./gradlew jmh -PjmhInclude='.*PlayerActionQueueBenchmark.*'`
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class PlayerActionQueueBenchmark {
  @Param("2", "4", "8") var players: Int = 2

  private lateinit var ring: Array<PlayerActionQueue>
  private lateinit var legacy: Array<LegacyCompositeActionQueue>
  private val out: ByteBuf = Unpooled.directBuffer(ACTION_BYTES)

  @Setup(Level.Trial)
  fun setUp() {
    ring =
      Array(players) {
        PlayerActionQueue(it + 1, numPlayers = players, gameBufferSize = 4096).also { q ->
          q.markSynced()
        }
      }
    legacy =
      Array(players) {
        LegacyCompositeActionQueue(it + 1, numPlayers = players, gameBufferSize = 4096).also { q ->
          q.markSynced()
        }
      }
  }

  @TearDown(Level.Trial)
  fun tearDown() {
    out.release()
  }

  @Benchmark
  fun ringBuffer(): Int {
    for (q in ring) {
      val input = PooledByteBufAllocator.DEFAULT.directBuffer(ACTION_BYTES).writeZero(ACTION_BYTES)
      q.addActions(input)
      input.release()
    }
    var total = 0
    for (reader in 0 until players) {
      for (q in ring) {
        out.clear()
        q.getActionAndWriteToArray(reader, out, ACTION_BYTES)
        total += out.writerIndex()
      }
    }
    return total
  }

  @Benchmark
  fun compositeByteBuf(): Int {
    for (q in legacy) {
      val input = PooledByteBufAllocator.DEFAULT.directBuffer(ACTION_BYTES).writeZero(ACTION_BYTES)
      q.addActions(input)
      input.release()
    }
    var total = 0
    for (reader in 0 until players) {
      for (q in legacy) {
        out.clear()
        q.getActionAndWriteToArray(reader, out, ACTION_BYTES)
        total += out.writerIndex()
      }
    }
    return total
  }

  private companion object {
    const val ACTION_BYTES = 8
  }
}

/** Verbatim copy of the previous [PlayerActionQueue], kept only as a benchmark baseline. */
class LegacyCompositeActionQueue(
  val playerNumber: Int,
  numPlayers: Int,
  private val gameBufferSize: Int,
) {
  private val data: CompositeByteBuf = Unpooled.compositeBuffer()

  // Total bytes currently available to read from the start of the buffer
  private var totalWrittenBytes = 0

  // How many bytes this specific player has read from the stream
  private var readPosition = 0

  /**
   * Whether the queue is synced with the [org.emulinker.kaillera.model.KailleraGame].
   *
   * Synced starts as `true` at the beginning of a game, and if it ever is set to false there is no
   * path where it will resync.
   */
  var synced = false
    private set

  fun markSynced() {
    synced = true
    if (data.numComponents() > 0) {
      data.removeComponents(0, data.numComponents())
    }
    data.clear()
    totalWrittenBytes = 0
    readPosition = 0
  }

  fun markDesynced() {
    synced = false
    // TODO(nue): See if this is the correct way to do this. Maybe there is a function to throw away
    // the rest of the bytes?
    if (data.refCnt() > 0) data.release()
  }

  /** Adds "actions" to the queue. */
  fun addActions(actions: ByteBuf) {
    if (!synced) {
      return
    }

    data.addComponent(true, actions.retain())
    totalWrittenBytes += actions.readableBytes()

    if (data.readableBytes() > gameBufferSize) {
      // Discard bytes from the beginning
      val toDiscard = data.readableBytes() - gameBufferSize
      data.skipBytes(toDiscard)
      data.discardReadBytes()

      for (i in heads.indices) {
        heads[i] = (heads[i] - toDiscard).coerceAtLeast(0)
      }
    }
  }

  private val heads = IntArray(numPlayers)

  fun getActionAndWriteToArray(readingPlayerIndex: Int, writeTo: ByteBuf, actionLength: Int) {
    if (!synced) {
      writeTo.writeZero(actionLength)
      return
    }

    if (containsNewDataForPlayer(readingPlayerIndex, actionLength)) {
      val relativeHead = heads[readingPlayerIndex]

      if (relativeHead + actionLength > data.writerIndex()) {
        // Should verify containsNewDataForPlayer check coverage
        throw IllegalStateException("Not enough data!")
      }

      writeTo.writeBytes(data, relativeHead, actionLength)

      heads[readingPlayerIndex] += actionLength

      cleanUp()
    } else {
      throw IllegalStateException("There is no data available for this synced user!")
    }
  }

  private fun cleanUp() {
    // Find the minimum head. We can discard data before that.
    var minHead = Int.MAX_VALUE
    for (h in heads) {
      if (h < minHead) minHead = h
    }

    if (minHead > 0) {
      data.readerIndex(minHead)
      data.discardReadBytes()
      for (i in heads.indices) {
        heads[i] -= minHead
      }
    }
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int): Boolean {
    val head = heads[playerIndex]
    val available = data.writerIndex() - head
    return available >= actionLength
  }
}
