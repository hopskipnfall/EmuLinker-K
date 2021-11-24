package org.emulinker.kaillera.model.exception

import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.exception.NewConnectionException
import java.lang.Exception

class ConnectionTypeException : LoginException {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, source: Exception?) : super(message, source) {}
}