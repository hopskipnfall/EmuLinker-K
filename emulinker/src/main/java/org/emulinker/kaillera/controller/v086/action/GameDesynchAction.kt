package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.model.event.GameDesynchEvent
import org.emulinker.util.EmuLang

@Singleton
class GameDesynchAction @Inject internal constructor() : V086GameEventHandler<GameDesynchEvent> {
  override var handledEventCount = 0
    private set

  override fun toString() = "GameDesynchAction"

  override fun handleEvent(event: GameDesynchEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      clientHandler.send(
        GameChatNotification(
          clientHandler.nextMessageNumber,
          EmuLang.getString("GameDesynchAction.DesynchDetected"),
          event.message
        )
      )
      // if (clientHandler.getUser().getStatus() == KailleraUser.STATUS_PLAYING)
      //	clientHandler.getUser().dropGame();
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct GameChat.Notification message")
    }
    // catch (DropGameException e)
    // {
    //	logger.atSevere().withCause(e).log("Failed to drop game during desynch");
    // }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
