package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.event.UserJoinedEvent

class LoginAction : V086Action<UserInformation>, V086ServerEventHandler<UserJoinedEvent> {
  override fun toString() = "LoginAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: UserInformation, clientHandler: V086ClientHandler) {
    val user: KailleraUser = clientHandler.user
    user.name = message.username
    user.clientType = message.clientType
    user.socketAddress = clientHandler.remoteSocketAddress
    user.connectionType = message.connectionType
    clientHandler.startSpeedTest()
    try {
      clientHandler.send(ServerAck(clientHandler.nextMessageNumber))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct ACK.ServerACK message")
    }
  }

  override fun handleEvent(event: UserJoinedEvent, clientHandler: V086ClientHandler) {
    try {
      val user = event.user
      clientHandler.send(
        UserJoined(
          clientHandler.nextMessageNumber,
          user.name!!,
          user.id,
          user.ping,
          user.connectionType,
        )
      )
      val thisUser = clientHandler.user
      if (thisUser.isEsfAdminClient && thisUser.accessLevel >= AccessManager.ACCESS_SUPERADMIN) {
        if (user != thisUser) {
          val sb = StringBuilder()
          sb.append(":USERINFO=")
          sb.append(user.id)
          sb.append(0x02.toChar())
          sb.append(user.connectSocketAddress.address.hostAddress)
          sb.append(0x02.toChar())
          sb.append(user.accessStr)
          sb.append(0x02.toChar())
          // str = u3.getName().replace(',','.');
          // str = str.replace(';','.');
          sb.append(user.name)
          sb.append(0x02.toChar())
          sb.append(user.ping)
          sb.append(0x02.toChar())
          sb.append(user.status)
          sb.append(0x02.toChar())
          sb.append(user.connectionType.byteValue.toInt())
          clientHandler.send(
            InformationMessage(clientHandler.nextMessageNumber, "server", sb.toString())
          )
        }
      }
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct UserJoined_Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
