package org.emulinker.kaillera.controller.v086.action

import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.KeepAlive

@Singleton
class KeepAliveAction @Inject internal constructor() : V086Action<KeepAlive> {
  override var actionPerformedCount = 0
    private set

  override fun toString() = "KeepAliveAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: KeepAlive, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    clientHandler.user.updateLastKeepAlive()
  }
}
