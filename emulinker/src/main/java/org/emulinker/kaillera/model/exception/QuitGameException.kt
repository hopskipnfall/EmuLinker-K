package org.emulinker.kaillera.model.exception

class QuitGameException : ActionException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
