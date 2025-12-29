package org.emulinker.util

import io.github.hopskipnfall.kaillera.protocol.netty.v086.NettyMessageFactory
import io.github.hopskipnfall.kaillera.protocol.v086.GameData as KmpGameData
import io.github.hopskipnfall.kaillera.protocol.v086.MessageFactory
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
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class GameDataBenchmark {
  // 24 bytes of game data
  private val contentBytes = ByteArray(24) { ((it % 255).toByte()) }

  private val legacyVariableSizeByteArray = VariableSizeByteArray(contentBytes)
  private val legacyGameData = LegacyGameData(123, legacyVariableSizeByteArray)

  private val kmpGameData = KmpGameData(123, contentBytes)

  private lateinit var legacyDeserializeBuffer: ByteBuf
  private lateinit var nettyDeserializeBuffer: ByteBuf
  private lateinit var kotlinxIoDeserializeBuffer: Buffer

  @Setup(Level.Trial)
  fun setup() {
    AppModule.charsetDoNotUse = Charsets.UTF_8

    legacyDeserializeBuffer = Unpooled.buffer(4096)
    nettyDeserializeBuffer = Unpooled.buffer(4096)
    kotlinxIoDeserializeBuffer = Buffer()

    NettyMessageFactory.write(legacyDeserializeBuffer, kmpGameData, AppModule.charsetDoNotUse)
    NettyMessageFactory.write(nettyDeserializeBuffer, kmpGameData, AppModule.charsetDoNotUse)
    kmpGameData.writeBodyTo(kotlinxIoDeserializeBuffer, "ISO-8859-1")
  }

  @TearDown(Level.Trial)
  fun tearDown() {
    legacyDeserializeBuffer.release()
    nettyDeserializeBuffer.release()
  }

  @Benchmark
  fun legacySerialize(blackhole: Blackhole) {
    val buf = Unpooled.buffer(legacyGameData.bodyBytes)

    legacyGameData.writeBodyTo(buf)
    blackhole.consume(buf)

    if (VALIDATE) {
      check(
        LegacyGameData.GameDataSerializer.read(buf, legacyGameData.messageNumber).getOrThrow() ==
          legacyGameData
      )
    }
    buf.release()
  }

  @Benchmark
  fun nettySerialize(blackhole: Blackhole) {
    val buf = Unpooled.buffer(kmpGameData.bodyBytes)

    NettyMessageFactory.write(buf, kmpGameData, AppModule.charsetDoNotUse)
    blackhole.consume(buf)

    if (VALIDATE) {
      check(
        NettyMessageFactory.read(
          kmpGameData.messageNumber,
          KmpGameData.ID,
          buf,
          AppModule.charsetDoNotUse,
        ) == kmpGameData
      )
    }
    buf.release()
  }

  @Benchmark
  fun kotlinxIoSerialize(blackhole: Blackhole) {
    val buffer = Buffer()

    kmpGameData.writeBodyTo(buffer, "ISO-8859-1")
    blackhole.consume(buffer)

    if (VALIDATE) {
      check(
        MessageFactory.read(buffer, kmpGameData.messageNumber, KmpGameData.ID, "ISO-8859-1") ==
          kmpGameData
      )
    }
  }

  @Benchmark
  fun legacyDeserialize(blackhole: Blackhole) {
    val buf = legacyDeserializeBuffer.slice()

    val result =
      LegacyGameData.GameDataSerializer.read(buf, legacyGameData.messageNumber).getOrThrow()
    blackhole.consume(result)

    if (VALIDATE) check(result == legacyGameData)
  }

  @Benchmark
  fun nettyDeserialize(blackhole: Blackhole) {
    val buf = nettyDeserializeBuffer.slice()

    val result =
      NettyMessageFactory.read(
        kmpGameData.messageNumber,
        KmpGameData.ID,
        buf,
        AppModule.charsetDoNotUse,
      )
    blackhole.consume(result)

    if (VALIDATE) check(result == kmpGameData)
  }

  @Benchmark
  fun kotlinxIoDeserialize(blackhole: Blackhole) {
    val buffer = kotlinxIoDeserializeBuffer.copy()

    val result = MessageFactory.read(buffer, 123, KmpGameData.ID, "ISO-8859-1")
    blackhole.consume(result)

    if (VALIDATE) check(result == kmpGameData)
  }

  companion object {
    /**
     * Whether the benchmarks should validate if the outcome was correct.
     *
     * Disabled by default so it doesn't add to the benchmark time.
     */
    const val VALIDATE = false
  }
}
