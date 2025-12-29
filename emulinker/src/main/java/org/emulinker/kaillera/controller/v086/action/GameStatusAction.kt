package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.github.hopskipnfall.kaillera.protocol.v086.GameStatus
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameStatusChangedEvent

class GameStatusAction : V086ServerEventHandler<GameStatusChangedEvent> {
  override fun toString() = "GameStatusAction"

  override fun handleEvent(event: GameStatusChangedEvent, clientHandler: V086ClientHandler) {
    try {
      val game = event.game
      val visiblePlayers = game.players.count { !it.inStealthMode }
      clientHandler.send(
        GameStatus(
          clientHandler.nextMessageNumber,
          game.id,
          0.toShort().toInt(),
          game.status,
          visiblePlayers,
          game.maxUsers,
        )
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct CreateGame.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
