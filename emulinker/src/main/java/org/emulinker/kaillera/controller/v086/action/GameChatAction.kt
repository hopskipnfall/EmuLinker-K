package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.commands.GameChatCommand
import org.emulinker.kaillera.controller.v086.protocol.*
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.impl.KailleraGameImpl

@Singleton
class GameChatAction
@Inject
internal constructor(
  private val gameOwnerCommandAction: GameOwnerCommandAction,
  private val gameChatCommands: @JvmSuppressWildcards List<GameChatCommand>,
) : V086Action<GameChatRequest>, V086GameEventHandler<GameChatEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "GameChatAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: GameChatRequest, clientHandler: V086ClientHandler) {
    if (clientHandler.user.game == null) return
    if (message.message.startsWith(ADMIN_COMMAND_ESCAPE_STRING)) {
      // if(clientHandler.getUser().getAccess() >= AccessManager.ACCESS_ADMIN ||
      // clientHandler.getUser().equals(clientHandler.getUser().getGame().getOwner())){
      try {
        if (gameOwnerCommandAction.isValidCommand((message as GameChat).message)) {
          gameOwnerCommandAction.performAction(message, clientHandler)
          if ((message as GameChat).message == "/help") checkCommands(message, clientHandler)
        } else {
          checkCommands(message, clientHandler)
        }
        return
      } catch (e: FatalActionException) {
        logger.atWarning().withCause(e).log("GameOwner command failed")
      }

      // }
    }
    actionPerformedCount++
    try {
      clientHandler.user.gameChat(message.message, message.messageNumber)
    } catch (e: GameChatException) {
      logger.atSevere().withCause(e).log("Failed to send game chat message")
    }
  }

  @Throws(FatalActionException::class)
  private fun checkCommands(message: GameChat, clientHandler: V086ClientHandler?) {
    var doCommand = true
    if (clientHandler!!.user.accessLevel < AccessManager.ACCESS_ELEVATED) {
      try {
        clientHandler.user.chat(":USER_COMMAND") // TODO(nue): What is this??
      } catch (e: ActionException) {
        doCommand = false
      }
    }
    val game: KailleraGameImpl = clientHandler.user.game ?: return
    if (doCommand) {
      val commandName =
        message.message
          .trim()
          .removePrefix(GameChatCommand.COMMAND_PREFIX)
          .split(" ", limit = 2)
          .first()
      val matchedCommand = gameChatCommands.firstOrNull { it.prefix == commandName }
      if (matchedCommand == null) {
        game.announce("Unknown Command: " + message.message, clientHandler.user)
      } else {
        matchedCommand.handle(message.message.trim(), game, clientHandler)
      }
    } else {
      game.announce("Denied: Flood Control", clientHandler.user)
    }
  }

  override fun handleEvent(gameChatEvent: GameChatEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      if (
        clientHandler.user.searchIgnoredUsers(
          gameChatEvent.user.connectSocketAddress.address.hostAddress
        )
      )
        return
      else if (clientHandler.user.ignoreAll) {
        if (
          gameChatEvent.user.accessLevel < AccessManager.ACCESS_ADMIN &&
            gameChatEvent.user !== clientHandler.user
        )
          return
      }
      val m = gameChatEvent.message
      clientHandler.send(
        GameChatNotification(clientHandler.nextMessageNumber, gameChatEvent.user.name!!, m)
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct GameChat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val ADMIN_COMMAND_ESCAPE_STRING = "/"
  }
}
