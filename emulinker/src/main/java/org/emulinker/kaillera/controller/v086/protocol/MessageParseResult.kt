package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.flogger.FluentLogger
import com.google.common.flogger.StackSize

sealed interface MessageParseResult<T : V086Message> {
  data class Failure<T : V086Message>(val message: String, val cause: Exception? = null) :
    MessageParseResult<T> {
    init {
      logger
        .atSevere()
        .withCause(cause)
        .withStackTrace(StackSize.FULL)
        .log("MessageParseResult.Failure: %s", message)
    }
  }

  data class Success<T : V086Message>(val message: T) : MessageParseResult<T>

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
