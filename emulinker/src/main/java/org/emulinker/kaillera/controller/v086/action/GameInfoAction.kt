package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.model.event.GameInfoEvent

@Singleton
class GameInfoAction @Inject internal constructor() : V086GameEventHandler<GameInfoEvent> {
  override var handledEventCount = 0
    private set

  override fun toString() = "GameInfoAction"

  override fun handleEvent(event: GameInfoEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
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
