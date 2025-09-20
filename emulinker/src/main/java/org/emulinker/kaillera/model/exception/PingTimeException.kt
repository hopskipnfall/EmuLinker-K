package org.emulinker.kaillera.model.exception

class PingTimeException : LoginException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
