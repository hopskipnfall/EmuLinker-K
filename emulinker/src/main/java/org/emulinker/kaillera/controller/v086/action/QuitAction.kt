package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.QuitNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitRequest
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.exception.ActionException

class QuitAction : V086Action<QuitRequest>, V086ServerEventHandler<UserQuitEvent> {
  override fun toString() = "QuitAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: QuitRequest, clientHandler: V086ClientHandler) {
    try {
      clientHandler.user.quit(message.message)
    } catch (e: ActionException) {
      throw FatalActionException("Failed to quit: " + e.message)
    }
  }

  override fun handleEvent(event: UserQuitEvent, clientHandler: V086ClientHandler) {
    try {
      val user = event.user
      clientHandler.send(QuitNotification(0, user.name!!, user.id, event.message))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct Quit.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
