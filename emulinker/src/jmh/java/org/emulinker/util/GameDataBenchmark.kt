package org.emulinker.util

import io.github.hopskipnfall.kaillera.protocol.netty.v086.NettyGameDataSerializer
import io.github.hopskipnfall.kaillera.protocol.netty.v086.NettyMessageFactory
import io.github.hopskipnfall.kaillera.protocol.v086.GameData as KmpGameData
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.util.concurrent.TimeUnit
import kotlinx.io.Buffer
import org.emulinker.kaillera.controller.v086.protocol.GameData as LegacyGameData
import org.emulinker.kaillera.pico.AppModule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class GameDataBenchmark {

  // 100 bytes of game data
  private val contentBytes = ByteArray(100) { ((it % 255).toByte()) }

  // Legacy objects
  private val legacyVariableSizeByteArray = VariableSizeByteArray(contentBytes)
  private val legacyGameData = LegacyGameData(123, legacyVariableSizeByteArray)
  private lateinit var legacyInputBuf: ByteBuf

  // KMP objects
  private val kmpGameData = KmpGameData(123, contentBytes)
  private lateinit var nettyInputBuf: ByteBuf
  private lateinit var kmpInputBuffer: Buffer

  // Reusable buffers for serialization to avoid allocation overhead during benchmark if possible
  // properly resetting them is key.
  // For JMH, usually we just allocate fresh or reset per invocation.
  // Given the request asks for "serialization and deserialization times",
  // usually we want to include the buffer write cost.

  @Setup(Level.Trial)
  fun setup() {
    AppModule.charsetDoNotUse = Charsets.UTF_8
    // Pre-fill buffers for Deserialization benchmarks

    // Legacy Deserialize Setup
    legacyInputBuf = Unpooled.buffer()
    LegacyGameData.GameDataSerializer.write(legacyInputBuf, legacyGameData)

    // Netty Deserialize Setup
    nettyInputBuf = Unpooled.buffer()
    NettyMessageFactory.write(nettyInputBuf, kmpGameData, AppModule.charsetDoNotUse)

    // Kotlinx-io Deserialize Setup
    kmpInputBuffer = Buffer()
    KmpGameData.GameDataSerializer.write(kmpInputBuffer, kmpGameData, "ISO-8859-1")
  }

  @Benchmark
  fun legacySerialize(): ByteBuf {
    val buffer = Unpooled.buffer()
    legacyGameData.writeTo(buffer)
    return buffer
  }

  @Benchmark
  fun nettySerialize(): ByteBuf {
    val buffer = Unpooled.buffer()
    NettyMessageFactory.write(buffer, kmpGameData, AppModule.charsetDoNotUse)
    return buffer
  }

  @Benchmark
  fun kotlinxIoSerialize(): Buffer {
    val buffer = Buffer()
    kmpGameData.writeBodyTo(buffer, "ISO-8859-1")
    return buffer
  }

  @Benchmark
  fun legacyDeserialize(): LegacyGameData {
    // We must slice or duplicate to avoid messing up the shared buffer reader index
    // .slice() is cheaper but shares content.
    // readerIndex should start at 0.
    val buf = legacyInputBuf.retainedSlice()
    buf.setIndex(0, buf.capacity())
    // Legacy serializer assumes 0x00 is checked or skipped?
    // GameData.GameDataSerializer.read(buffer, messageNumber)
    // It checks readableBytes < 4.
    val result = LegacyGameData.GameDataSerializer.read(buf, 123)
    // Release valid because we used retainedSlice?
    // Actually, Result<GameData> might hold reference?
    // Legacy GameData creates deep copy of VariableSizeByteArray in read()?
    // It does: buffer.get(gameData) -> copies bytes.
    buf.release()
    return result.getOrThrow()
  }

  @Benchmark
  fun nettyDeserialize(): KmpGameData {
    val buf = nettyInputBuf.retainedSlice()
    buf.setIndex(0, buf.capacity())
    val result = NettyMessageFactory.read( 123, KmpGameData.ID,buf, AppModule.charsetDoNotUse)
    buf.release()
    return result as KmpGameData
  }

  @Benchmark
  fun kotlinxIoDeserialize(): KmpGameData {
    // kotlinx.io Buffer is a bit different. We can't just "slice" and reset easily without copy if
    // we consume it.
    // We can use `copy()` which makes a deep copy of the buffer.
    val copy = kmpInputBuffer.copy()
    // KmpGameDataSerializer reads 0x00, short len, then bytes.
    val result = KmpGameData.GameDataSerializer.read(copy, 123, "ISO-8859-1")
    return result
  }
}
