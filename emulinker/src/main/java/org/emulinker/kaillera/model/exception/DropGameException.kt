package org.emulinker.kaillera.model.exception

class DropGameException : ActionException {
  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
