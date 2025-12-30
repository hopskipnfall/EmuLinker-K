package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.github.hopskipnfall.kaillera.protocol.v086.CloseGame
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameClosedEvent

class CloseGameAction : V086ServerEventHandler<GameClosedEvent> {
  override fun toString() = "CloseGameAction"

  override fun handleEvent(event: GameClosedEvent, clientHandler: V086ClientHandler) {
    try {
      clientHandler.send(CloseGame(clientHandler.nextMessageNumber, event.game.id, 0))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct CloseGame_Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
