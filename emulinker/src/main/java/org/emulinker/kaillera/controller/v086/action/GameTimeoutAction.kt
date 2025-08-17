package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameTimeoutEvent

class GameTimeoutAction : V086GameEventHandler<GameTimeoutEvent> {
  override fun toString() = "GameTimeoutAction"

  override fun handleEvent(event: GameTimeoutEvent, clientHandler: V086ClientHandler) {
    val player = event.user
    val user = clientHandler.user
    if (player == user) {
      logger
        .atFine()
        .log(
          "%s received timeout event %d for %s: resending messages...",
          user,
          event.timeoutNumber,
          event.game,
        )
      clientHandler.resend(event.timeoutNumber)
    } else {
      logger
        .atFine()
        .log(
          "%s received timeout event %d from %s for %s",
          user,
          event.timeoutNumber,
          player,
          event.game,
        )
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
