package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.JoinGameNotification
import org.emulinker.kaillera.controller.v086.protocol.JoinGameRequest
import org.emulinker.kaillera.controller.v086.protocol.PlayerInformation
import org.emulinker.kaillera.controller.v086.protocol.PlayerInformation.Player
import org.emulinker.kaillera.controller.v086.protocol.QuitGameNotification
import org.emulinker.kaillera.model.event.GameInfoEvent
import org.emulinker.kaillera.model.event.UserJoinedGameEvent
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.util.EmuLang
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class JoinGameAction :
  V086Action<JoinGameRequest>, V086GameEventHandler<UserJoinedGameEvent>, KoinComponent {
  private val joinGameMessages: List<String> by inject(named("joinGameMessages"))

  override fun toString() = "JoinGameAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: JoinGameRequest, clientHandler: V086ClientHandler) {
    try {
      val game = clientHandler.user.joinGame(message.gameId)

      for (msg in joinGameMessages) {
        clientHandler.user.queueEvent(GameInfoEvent(game, msg, toUser = clientHandler.user))
      }
    } catch (e: JoinGameException) {
      logger.atSevere().withCause(e).log("Failed to join game.")
      try {
        clientHandler.send(
          InformationMessage(
            clientHandler.nextMessageNumber,
            "server",
            EmuLang.getString("JoinGameAction.JoinGameDenied", e.message),
          )
        )
        clientHandler.send(
          QuitGameNotification(
            clientHandler.nextMessageNumber,
            clientHandler.user.name ?: "(empty username)",
            clientHandler.user.id,
          )
        )
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct new Message")
      }
    }
  }

  override fun handleEvent(event: UserJoinedGameEvent, clientHandler: V086ClientHandler) {
    val thisUser = clientHandler.user
    try {
      val game = event.game
      val user = event.user
      if (user == thisUser) {
        val players: MutableList<Player> = ArrayList()
        game.players
          .asSequence()
          .filter { it != thisUser && !it.inStealthMode }
          .mapTo(players) { PlayerInformation.Player(it.name!!, it.ping, it.id, it.connectionType) }
        clientHandler.send(PlayerInformation(clientHandler.nextMessageNumber, players))
      }
      if (!user.inStealthMode)
        clientHandler.send(
          JoinGameNotification(
            clientHandler.nextMessageNumber,
            game.id,
            0,
            user.name!!,
            user.ping,
            user.id,
            user.connectionType,
          )
        )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct JoinGame.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
