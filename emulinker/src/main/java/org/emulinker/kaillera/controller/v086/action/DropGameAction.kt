package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.PlayerDrop
import org.emulinker.kaillera.model.event.UserDroppedGameEvent
import org.emulinker.kaillera.model.exception.DropGameException

@Singleton
class DropGameAction @Inject internal constructor() :
  V086Action<PlayerDrop.Request>, V086GameEventHandler<UserDroppedGameEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "DropGameAction"

  @Throws(FatalActionException::class)
  override suspend fun performAction(
    message: PlayerDrop.Request,
    clientHandler: V086ClientHandler
  ) {
    actionPerformedCount++
    try {
      clientHandler.user.dropGame()
    } catch (e: DropGameException) {
      logger.atFine().withCause(e).log("Failed to drop game")
    }
  }

  override suspend fun handleEvent(event: UserDroppedGameEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      val user = event.user
      val playerNumber = event.playerNumber
      //			clientHandler.send(PlayerDrop.Notification.create(clientHandler.getNextMessageNumber(),
      // user.getName(), (byte) game.getPlayerNumber(user)));
      if (!user.inStealthMode)
        clientHandler.send(
          PlayerDrop.Notification(
            clientHandler.nextMessageNumber,
            user.userData.name,
            playerNumber.toByte()
          )
        )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct PlayerDrop.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
