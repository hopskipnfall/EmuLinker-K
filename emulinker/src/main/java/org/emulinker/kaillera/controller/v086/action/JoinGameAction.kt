package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.*
import org.emulinker.kaillera.controller.v086.protocol.PlayerInformation.Player
import org.emulinker.kaillera.model.event.UserJoinedGameEvent
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.util.EmuLang

@Singleton
class JoinGameAction @Inject internal constructor() :
  V086Action<JoinGameRequest>, V086GameEventHandler<UserJoinedGameEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "JoinGameAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: JoinGameRequest, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    try {
      clientHandler.user.joinGame(message.gameId)
    } catch (e: JoinGameException) {
      logger.atSevere().withCause(e).log("Failed to join game.")
      try {
        clientHandler.send(
          InformationMessage(
            clientHandler.nextMessageNumber,
            "server",
            EmuLang.getString("JoinGameAction.JoinGameDenied", e.message)
          )
        )
        clientHandler.send(
          QuitGameNotification(
            clientHandler.nextMessageNumber,
            clientHandler.user.name!!,
            clientHandler.user.id
          )
        )
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct new Message")
      }
    }
  }

  override fun handleEvent(event: UserJoinedGameEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    val thisUser = clientHandler.user
    try {
      val game = event.game
      val user = event.user
      if (user == thisUser) {
        val players: MutableList<Player> = ArrayList()
        game.players
          .asSequence()
          .filter { it != thisUser && !it.inStealthMode }
          .mapTo(players) {
            PlayerInformation.Player(it.name!!, it.ping.toLong(), it.id, it.connectionType)
          }
        clientHandler.send(PlayerInformation(clientHandler.nextMessageNumber, players))
      }
      if (!user.inStealthMode)
        clientHandler.send(
          JoinGameNotification(
            clientHandler.nextMessageNumber,
            game.id,
            0,
            user.name!!,
            user.ping.toLong(),
            user.id,
            user.connectionType
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
