package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameTimeoutEvent

@Singleton
class GameTimeoutAction @Inject internal constructor() : V086GameEventHandler<GameTimeoutEvent> {
  override var handledEventCount = 0
    private set

  override fun toString() = "GameTimeoutAction"

  override suspend fun handleEvent(event: GameTimeoutEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    val player = event.user
    val user = clientHandler.user
    if (player == user) {
      logger
          .atFine()
          .log(
              "%s received timeout event %d for %s: resending messages...",
              user,
              event.timeoutNumber,
              event.game)
      clientHandler.resend(event.timeoutNumber)
    } else {
      logger
          .atFine()
          .log(
              "%s received timeout event %d from %s for %s",
              user,
              event.timeoutNumber,
              player,
              event.game)
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
