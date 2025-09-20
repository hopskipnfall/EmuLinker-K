package org.emulinker.kaillera.model.exception

open class LoginException : ActionException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
