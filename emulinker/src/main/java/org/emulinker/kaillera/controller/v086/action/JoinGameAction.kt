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
    V086Action<JoinGame_Request>, V086GameEventHandler<UserJoinedGameEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "JoinGameAction"

  @Throws(FatalActionException::class)
  override suspend fun performAction(message: JoinGame_Request, clientHandler: V086ClientHandler) {
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
                EmuLang.getString("JoinGameAction.JoinGameDenied", e.message)))
        clientHandler.send(
            QuitGame_Notification(
                clientHandler.nextMessageNumber,
                clientHandler.user.userData.name,
                clientHandler.user.userData.id))
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct new Message")
      }
    }
  }

  override suspend fun handleEvent(event: UserJoinedGameEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    val thisUser = clientHandler.user
    try {
      val game = event.game
      val user = event.user
      if (user == thisUser) {
        val players: MutableList<Player> = ArrayList()
        game.players.asSequence().filter { it != thisUser && !it.inStealthMode }.mapTo(players) {
          PlayerInformation.Player(
              it.userData.name, it.ping.toLong(), it.userData.id, it.connectionType)
        }
        clientHandler.send(PlayerInformation(clientHandler.nextMessageNumber, players))
      }
      if (!user.inStealthMode)
          clientHandler.send(
              JoinGame_Notification(
                  clientHandler.nextMessageNumber,
                  game.id,
                  0,
                  user.userData.name,
                  user.ping.toLong(),
                  user.userData.id,
                  user.connectionType))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct JoinGame_Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
