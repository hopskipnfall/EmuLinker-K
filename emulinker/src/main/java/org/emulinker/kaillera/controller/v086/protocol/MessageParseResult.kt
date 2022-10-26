package org.emulinker.kaillera.controller.v086.protocol

sealed interface MessageParseResult<T : V086Message> {
  data class Failure<T : V086Message>(val message: String, val cause: Exception? = null) :
    MessageParseResult<T>

  data class Success<T : V086Message>(val message: T) : MessageParseResult<T>
}
