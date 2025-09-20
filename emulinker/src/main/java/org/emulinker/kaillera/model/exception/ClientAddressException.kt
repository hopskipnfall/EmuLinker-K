package org.emulinker.kaillera.model.exception

class ClientAddressException : LoginException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
