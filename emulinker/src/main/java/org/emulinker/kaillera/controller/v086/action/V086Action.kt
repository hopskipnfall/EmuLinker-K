package org.emulinker.kaillera.controller.v086.action

import io.github.hopskipnfall.kaillera.protocol.v086.V086Message
import org.emulinker.kaillera.controller.v086.V086ClientHandler

interface V086Action<T : V086Message> {
  override fun toString(): String

  @Throws(FatalActionException::class)
  fun performAction(message: T, clientHandler: V086ClientHandler)
}
