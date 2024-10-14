package org.emulinker.kaillera.controller.v086.action

import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.KeepAlive

class KeepAliveAction : V086Action<KeepAlive> {
  override var actionPerformedCount = 0
    private set

  override fun toString() = "KeepAliveAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: KeepAlive, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    clientHandler.user.updateLastKeepAlive()
  }
}
