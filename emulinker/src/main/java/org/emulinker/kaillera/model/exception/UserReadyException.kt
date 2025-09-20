package org.emulinker.kaillera.model.exception

class UserReadyException(message: String?, source: Exception? = null) :
  ActionException(message, source)
