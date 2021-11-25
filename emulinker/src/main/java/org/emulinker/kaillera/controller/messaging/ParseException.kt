package org.emulinker.kaillera.controller.messaging

import java.lang.Exception

class ParseException : Exception {
  constructor() : super()
  constructor(msg: String?) : super(msg)
  constructor(msg: String?, cause: Throwable?) : super(msg, cause)
}