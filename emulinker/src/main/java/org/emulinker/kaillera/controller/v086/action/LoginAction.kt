package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.Ack
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.event.UserJoinedEvent

@Singleton
class LoginAction @Inject internal constructor() :
  V086Action<UserInformation>, V086ServerEventHandler<UserJoinedEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "LoginAction"

  @Throws(FatalActionException::class)
  override suspend fun performAction(message: UserInformation, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    val user: KailleraUser = clientHandler.user
    user.userData = user.userData.copy(name = message.username)
    user.clientType = message.clientType
    user.socketAddress = clientHandler.remoteSocketAddress
    user.connectionType = message.connectionType
    clientHandler.startSpeedTest()
    try {
      clientHandler.send(Ack.ServerAck(clientHandler.nextMessageNumber))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct ACK.ServerACK message")
    }
  }

  override suspend fun handleEvent(event: UserJoinedEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      val user = event.user
      clientHandler.send(
        UserJoined(
          clientHandler.nextMessageNumber,
          user.userData.name,
          user.userData.id,
          user.ping.toLong(),
          user.connectionType
        )
      )
      val thisUser = clientHandler.user
      if (thisUser.isEmuLinkerClient && thisUser.accessLevel >= AccessManager.ACCESS_SUPERADMIN) {
        if (user != thisUser) {
          val sb = StringBuilder()
          sb.append(":USERINFO=")
          sb.append(user.userData.id)
          sb.append(0x02.toChar())
          sb.append(user.connectSocketAddress.address.hostAddress)
          sb.append(0x02.toChar())
          sb.append(user.accessStr)
          sb.append(0x02.toChar())
          // str = u3.getName().replace(',','.');
          // str = str.replace(';','.');
          sb.append(user.userData.name)
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
