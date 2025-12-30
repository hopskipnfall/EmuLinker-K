package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.github.hopskipnfall.kaillera.protocol.v086.GameChatNotification
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameInfoEvent

class GameInfoAction : V086GameEventHandler<GameInfoEvent> {
  override fun toString() = "GameInfoAction"

  override fun handleEvent(event: GameInfoEvent, clientHandler: V086ClientHandler) {
    if (event.toUser != null) {
      if (event.toUser !== clientHandler.user) return
    }
    try {
      clientHandler.send(
        GameChatNotification(clientHandler.nextMessageNumber, "Server", event.message)
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct GameChat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
