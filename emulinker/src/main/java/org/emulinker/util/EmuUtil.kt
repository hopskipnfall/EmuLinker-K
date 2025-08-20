package org.emulinker.util

import com.codahale.metrics.Timer
import io.netty.buffer.ByteBuf
import java.io.File
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.DurationUnit.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.format
import kotlinx.datetime.offsetIn
import org.emulinker.kaillera.pico.AppModule

object EmuUtil {
  val LB: String = System.getProperty("line.separator")
  var DATE_FORMAT: DateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm:ss")

  // TODO(nue): This looks like a hack. Maybe clean it up.
  fun systemIsWindows(): Boolean {
    return File.separatorChar == '\\'
  }

  fun formatSocketAddress(sa: SocketAddress): String {
    return ((sa as InetSocketAddress).address.hostAddress + ":" + sa.port)
  }

  fun dumpBuffer(buffer: ByteBuffer, allHex: Boolean = false): String {
    val sb = StringBuilder()
    buffer.mark()
    while (buffer.hasRemaining()) {
      val b = buffer.get()
      if (!allHex && Character.isLetterOrDigit(Char(b.toUShort()))) sb.append(Char(b.toUShort()))
      else sb.append(b.toHexString())
      if (buffer.hasRemaining()) sb.append(",")
    }
    buffer.reset()
    return sb.toString()
  }

  fun ByteBuf.dumpToByteArray(): ByteArray {
    val arr = ByteArray(this.readableBytes() + this.readerIndex())
    this.getBytes(0, arr)
    return arr
  }

  fun ByteBuffer.dumpToByteArray(): ByteArray {
    val pos = position()
    position(0)
    val arr = ByteArray(remaining())
    get(/* index= */ 0, arr)
    position(pos)
    return arr
  }

  fun ByteBuffer.readString(
    stopByte: Int = 0x00,
    charset: Charset = AppModule.charsetDoNotUse,
  ): String {
    val tempBuffer = ByteBuffer.allocate(this.remaining())
    while (this.hasRemaining()) {
      var b: Byte
      if (this.get().also { b = it }.toInt() == stopByte) break
      tempBuffer.put(b)
    }
    return charset.decode(tempBuffer.flip() as ByteBuffer).toString()
  }

  fun ByteBuf.readString(
    stopByte: Int = 0x00,
    charset: Charset = AppModule.charsetDoNotUse,
  ): String {
    val tempBuffer = ByteBuffer.allocate(this.readableBytes())
    while (this.readableBytes() > 0) {
      var b: Byte
      if (this.readByte().also { b = it }.toInt() == stopByte) break
      tempBuffer.put(b)
    }
    return charset.decode(tempBuffer.flip() as ByteBuffer).toString()
  }

  fun writeString(
    buffer: ByteBuffer,
    s: String,
    stopByte: Int = 0x00,
    charset: Charset = AppModule.charsetDoNotUse,
  ) {
    buffer.put(charset.encode(s))
    //		char[] tempArray = s.toCharArray();
    //		for(int i=0; i<tempArray.length; i++)
    //			buffer.put((byte) tempArray[i]);
    buffer.put(stopByte.toByte())
  }

  fun writeString(
    buffer: ByteBuf,
    s: String,
    stopByte: Int = 0x00,
    charset: Charset = AppModule.charsetDoNotUse,
  ) {
    buffer.writeBytes(charset.encode(s))
    buffer.writeByte(stopByte)
  }

  @Throws(InstantiationException::class)
  fun construct(className: String, args: Array<Any>): Any {
    return try {
      val c = Class.forName(className)
      val constructorArgs: Array<Class<*>?> = arrayOfNulls(args.size)
      for (i in args.indices) constructorArgs[i] = args[i].javaClass
      val constructor = c.getConstructor(*constructorArgs)
      constructor.newInstance(*args)
    } catch (e: Exception) {
      throw InstantiationException("Problem constructing new " + className + ": " + e.message)
    }
  }

  fun Instant.toSimpleUtcDatetime(): String =
    DateTimeComponents.Formats.RFC_1123.format {
      setDateTimeOffset(
        this@toSimpleUtcDatetime,
        this@toSimpleUtcDatetime.offsetIn(TimeZone.currentSystemDefault()),
      )
    }

  /**
   * Similar to [Duration.toString()] except localized to the language.
   *
   * For example: 5s in English and 5秒 in Japanese.
   */
  fun Duration.toLocalizedString(unit: DurationUnit, decimals: Int = 0): String {
    val unitSuffix =
      when (unit) {
        NANOSECONDS -> "ns"
        MICROSECONDS -> "us"
        MILLISECONDS -> "ms"
        SECONDS -> "s"
        MINUTES -> "m"
        HOURS -> "h"
        DAYS -> "d"
      }

    val i18nTemplate =
      when (unit) {
        NANOSECONDS,
        MICROSECONDS,
        MILLISECONDS,
        HOURS,
        DAYS -> TODO("Unit $unit not yet supported")
        SECONDS -> "Time.SecondsAbbreviation"
        MINUTES -> "Time.MinutesAbbreviation"
      }

    val number = this.toString(unit, decimals).removeSuffix(unitSuffix)
    return EmuLang.getString(i18nTemplate, number)
  }

  // TODO(nue): Get rid of this after it's confirmed it can be safely removed.
  /** NOOP placeholder for a function that _used to_ call [Thread.sleep]. */
  @Deprecated(
    message = "We no longer sleep. Should be inlined.",
    ReplaceWith("Thread.yield()"),
    DeprecationLevel.WARNING,
  )
  fun threadSleep(d: Duration) {
    Thread.yield()
  }

  fun min(a: Duration, b: Duration) = if (a <= b) a else b

  inline fun <R> Timer.timeKt(toTime: () -> R): R {
    var out: R
    this.update(measureNanoTime { out = toTime() }, TimeUnit.NANOSECONDS)
    return out
  }

  fun Duration.toMillisDouble(): Double =
    this.inWholeNanoseconds / 1.milliseconds.inWholeNanoseconds.toDouble()

  fun Duration.toSecondDoublePrecisionString() =
    if (this < 1.minutes) toLocalizedString(SECONDS, 2) else toLocalizedString(MINUTES, 1)
}
