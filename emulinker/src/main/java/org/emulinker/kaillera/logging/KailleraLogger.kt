package org.emulinker.kaillera.logging

import com.google.common.flogger.AbstractLogger
import com.google.common.flogger.LogContext
import com.google.common.flogger.LoggingApi
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.Platform
import com.google.common.flogger.parser.DefaultPrintfMessageParser
import com.google.common.flogger.parser.MessageParser
import com.google.common.flogger.util.CallerFinder
import java.util.logging.Level

class KailleraLogger(backend: LoggerBackend) : AbstractLogger<KailleraLogger.Api>(backend) {
  override fun at(level: Level): Api? {
    val isLoggable = isLoggable(level)
    val isForced = Platform.shouldForceLogging(getName(), level, isLoggable)
    return if (isLoggable || isForced) this.Context(level, isForced) else NoOp
  }

  interface Api : LoggingApi<Api>

  // VisibleForTesting
  inner class Context internal constructor(level: Level, isForced: Boolean) :
    LogContext<KailleraLogger, Api>(level, isForced), Api {
    override fun getLogger(): KailleraLogger = this@KailleraLogger

    override fun api(): Api = this

    override fun noOp(): Api = NoOp

    override fun getMessageParser(): MessageParser? {
      return DefaultPrintfMessageParser.getInstance()
    }
  }

  companion object {
    private object NoOp : LoggingApi.NoOp<Api>(), Api

    fun forEnclosingClass(): KailleraLogger {
      /**
       * Fork of [com.google.common.flogger.backend.system.StackBasedCallerFinder.findLoggingClass]
       * that allows any class. This is necessary because [forEnclosingClass] is on the companion
       * object, not the class itself.
       */
      fun <N> findLoggingClass(loggerClass: Class<out N>): String {
        // We can skip at most only 1 method from the analysis, the inferLoggingClass() method
        // itself.
        return CallerFinder.findCallerOf(loggerClass, 1)?.className
          ?: throw java.lang.IllegalStateException(
            "no caller found on the stack for: " + loggerClass.getName()
          )
      }

      val loggingClass = findLoggingClass(Companion::class.java)
      return KailleraLogger(Platform.getBackend(loggingClass))
    }
  }
}
