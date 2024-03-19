package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.model.event.PlayerDesynchEvent
import org.emulinker.util.EmuLang

@Singleton
class PlayerDesynchAction @Inject internal constructor() :
  V086GameEventHandler<PlayerDesynchEvent> {
  override var handledEventCount = 0
    private set

  override fun toString(): String = PlayerDesynchAction::class.java.simpleName

  override fun handleEvent(event: PlayerDesynchEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      clientHandler.send(
        GameChatNotification(
          clientHandler.nextMessageNumber,
          EmuLang.getString("PlayerDesynchAction.DesynchDetected"),
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
