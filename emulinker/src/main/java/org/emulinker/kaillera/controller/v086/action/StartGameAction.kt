package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChat
import org.emulinker.kaillera.controller.v086.protocol.StartGame
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.exception.StartGameException

@Singleton
class StartGameAction
@Inject
internal constructor(private val lookingForGameReporter: TwitterBroadcaster) :
  V086Action<StartGame.Request>, V086GameEventHandler<GameStartedEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "StartGameAction"

  override suspend fun performAction(message: StartGame.Request, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    try {
      clientHandler.user.startGame()
    } catch (e: StartGameException) {
      logger.atFine().withCause(e).log("Failed to start game")
      try {
        clientHandler.send(
          GameChat.Notification(clientHandler.nextMessageNumber, "Error", e.message!!)
        )
      } catch (ex: MessageFormatException) {
        logger.atSevere().withCause(ex).log("Failed to construct GameChat.Notification message")
      }
    }
  }

  override suspend fun handleEvent(event: GameStartedEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
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
        StartGame.Notification(
          clientHandler.nextMessageNumber,
          delay.toShort().toInt(),
          playerNumber.toByte().toShort(),
          game.players.size.toByte().toShort()
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
