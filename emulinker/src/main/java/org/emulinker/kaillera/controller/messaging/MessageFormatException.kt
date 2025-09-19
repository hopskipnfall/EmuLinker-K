package org.emulinker.kaillera.controller.messaging

@Deprecated("We should replace this with something more elegant", level = DeprecationLevel.WARNING)
class MessageFormatException(msg: String? = null, cause: Throwable? = null) :
  IllegalArgumentException(msg, cause)
