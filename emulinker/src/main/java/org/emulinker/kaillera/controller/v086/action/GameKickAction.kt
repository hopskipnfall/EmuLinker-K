package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.controller.v086.protocol.GameKick
import org.emulinker.kaillera.model.exception.GameKickException

class GameKickAction : V086Action<GameKick> {
  override fun toString() = "GameKickAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: GameKick, clientHandler: V086ClientHandler) {
    try {
      clientHandler.user.gameKick(message.userId)
    } catch (e: GameKickException) {
      logger.atSevere().withCause(e).log("Failed to kick")
      try {
        clientHandler.send(
          GameChatNotification(0, "Error", e.message ?: "")
        )
      } catch (ex: MessageFormatException) {
        logger.atSevere().withCause(ex).log("Failed to construct GameChat.Notification message")
      }
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
