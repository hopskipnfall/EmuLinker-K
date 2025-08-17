package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.CreateGame
import org.emulinker.kaillera.controller.v086.protocol.CreateGameNotification
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.QuitGameNotification
import org.emulinker.kaillera.model.event.GameCreatedEvent
import org.emulinker.kaillera.model.event.GameInfoEvent
import org.emulinker.kaillera.model.exception.CreateGameException
import org.emulinker.kaillera.model.exception.FloodException
import org.emulinker.util.EmuLang
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class CreateGameAction :
  V086Action<CreateGame>, V086ServerEventHandler<GameCreatedEvent>, KoinComponent {
  private val joinGameMessages: List<String> by inject(named("joinGameMessages"))

  override fun toString() = "CreateGameAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: CreateGame, clientHandler: V086ClientHandler) {
    try {
      val game = clientHandler.user.createGame(message.romName)

      if (game != null) {
        for (msg in joinGameMessages) {
          clientHandler.user.queueEvent(GameInfoEvent(game, msg, toUser = clientHandler.user))
        }
      }
    } catch (e: CreateGameException) {
      logger
        .atInfo()
        .withCause(e)
        .log("Create Game Denied: %s: %s", clientHandler.user, message.romName)
      try {
        clientHandler.send(
          InformationMessage(
            clientHandler.nextMessageNumber,
            "server",
            EmuLang.getString("CreateGameAction.CreateGameDenied", e.message),
          )
        )
        // TODO(nue): If clientHandler.user.name == null (meaning the user is not logged in) do we
        // need to send this?
        clientHandler.send(
          QuitGameNotification(
            clientHandler.nextMessageNumber,
            clientHandler.user.name ?: "",
            clientHandler.user.id,
          )
        )
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct message")
      }
    } catch (e: FloodException) {
      logger
        .atInfo()
        .withCause(e)
        .log("Create Game Denied: %s: %s", clientHandler.user, message.romName)
      try {
        clientHandler.send(
          InformationMessage(
            clientHandler.nextMessageNumber,
            "server",
            EmuLang.getString("CreateGameAction.CreateGameDeniedFloodControl"),
          )
        )
        // TODO(nue): If clientHandler.user.name == null (meaning the user is not logged in) do we
        // need to send this?
        clientHandler.send(
          QuitGameNotification(
            clientHandler.nextMessageNumber,
            clientHandler.user.name ?: "",
            clientHandler.user.id,
          )
        )
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct message")
      }
    }
  }

  override fun handleEvent(event: GameCreatedEvent, clientHandler: V086ClientHandler) {
    try {
      val game = event.game
      val owner = game.owner
      clientHandler.send(
        CreateGameNotification(
          clientHandler.nextMessageNumber,
          owner!!.name!!,
          game.romName,
          owner.clientType!!,
          game.id,
          0.toShort().toInt(),
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
