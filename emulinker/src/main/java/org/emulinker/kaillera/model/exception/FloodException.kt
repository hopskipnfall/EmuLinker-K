package org.emulinker.kaillera.model.exception

class FloodException : ActionException {
  constructor() : super()

  constructor(message: String?) : super(message)

  constructor(message: String?, source: Exception?) : super(message, source)
}
