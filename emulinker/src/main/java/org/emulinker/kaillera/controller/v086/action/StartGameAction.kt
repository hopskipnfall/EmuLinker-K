package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.github.hopskipnfall.kaillera.protocol.v086.GameChatNotification
import io.github.hopskipnfall.kaillera.protocol.v086.StartGameNotification
import io.github.hopskipnfall.kaillera.protocol.v086.StartGameRequest
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.exception.StartGameException

class StartGameAction(private val lookingForGameReporter: TwitterBroadcaster) :
  V086Action<StartGameRequest>, V086GameEventHandler<GameStartedEvent> {
  override fun toString() = "StartGameAction"

  override fun performAction(message: StartGameRequest, clientHandler: V086ClientHandler) {
    try {
      clientHandler.user.startGame()
    } catch (e: StartGameException) {
      logger.atFine().withCause(e).log("Failed to start game")
      try {
        clientHandler.send(
          GameChatNotification(clientHandler.nextMessageNumber, "Error", e.message!!)
        )
      } catch (ex: MessageFormatException) {
        logger.atSevere().withCause(ex).log("Failed to construct GameChat.Notification message")
      }
    }
  }

  override fun handleEvent(event: GameStartedEvent, clientHandler: V086ClientHandler) {
    try {
      val game = event.game
      clientHandler.user.tempDelay = game.highestUserFrameDelay - clientHandler.user.frameDelay
      val delay: Int =
        if (game.sameDelay) {
          game.highestUserFrameDelay
        } else {
          clientHandler.user.frameDelay
        }
      val playerNumber = game.getPlayerNumber(clientHandler.user)
      clientHandler.send(
        StartGameNotification(
          messageNumber = clientHandler.nextMessageNumber,
          numPlayers = game.players.size,
          playerNumber = playerNumber,
          val1 = delay,
        )
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct StartGame.Notification message")
    }
    lookingForGameReporter.cancelActionsForGame(event.game.id)
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
