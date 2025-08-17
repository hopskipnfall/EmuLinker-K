package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.model.event.InfoMessageEvent

class InfoMessageAction : V086UserEventHandler<InfoMessageEvent> {
  override fun toString() = "InfoMessageAction"

  override fun handleEvent(event: InfoMessageEvent, clientHandler: V086ClientHandler) {
    try {
      clientHandler.send(
        InformationMessage(clientHandler.nextMessageNumber, "server", event.message)
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct InformationMessage message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
