package org.emulinker.kaillera.model.exception

import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.exception.NewConnectionException
import java.lang.Exception

open class NewConnectionException : ActionException {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, source: Exception?) : super(message, source) {}
}