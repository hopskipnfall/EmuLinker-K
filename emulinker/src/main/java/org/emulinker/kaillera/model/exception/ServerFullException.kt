package org.emulinker.kaillera.model.exception

class ServerFullException : NewConnectionException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
