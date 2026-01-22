package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.PlayerDropNotification
import org.emulinker.kaillera.controller.v086.protocol.PlayerDropRequest
import org.emulinker.kaillera.model.event.UserDroppedGameEvent
import org.emulinker.kaillera.model.exception.DropGameException

class DropGameAction : V086Action<PlayerDropRequest>, V086GameEventHandler<UserDroppedGameEvent> {
  override fun toString() = "DropGameAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: PlayerDropRequest, clientHandler: V086ClientHandler) {
    try {
      clientHandler.user.dropGame()
    } catch (e: DropGameException) {
      logger.atFine().withCause(e).log("Failed to drop game")
    }
  }

  override fun handleEvent(event: UserDroppedGameEvent, clientHandler: V086ClientHandler) {
    try {
      val user = event.user
      val playerNumber = event.playerNumber
      //			clientHandler.send(PlayerDrop.Notification.create(clientHandler.getNextMessageNumber(),
      // user.getName(), (byte) game.getPlayerNumber(user)));
      if (!user.inStealthMode)
        clientHandler.send(PlayerDropNotification(0, user.name!!, playerNumber.toByte()))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct PlayerDrop.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
