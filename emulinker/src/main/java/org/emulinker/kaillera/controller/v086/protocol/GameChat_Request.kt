package org.emulinker.kaillera.controller.v086.protocol

import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.protocol.V086Message.Companion.validateMessageNumber

data class GameChat_Request
@Throws(MessageFormatException::class)
constructor(override val messageNumber: Int, override val message: String) : GameChat() {

  override val messageId = ID
  override val username = ""

  init {
    validateMessageNumber(messageNumber)
  }
}
