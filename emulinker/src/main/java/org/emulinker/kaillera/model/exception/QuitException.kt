package org.emulinker.kaillera.model.exception

class QuitException : ActionException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
