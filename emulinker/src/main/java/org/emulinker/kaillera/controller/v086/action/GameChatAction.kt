package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.CommandRegistry
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.controller.v086.protocol.GameChatRequest
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.util.EmuLang

private const val COMMAND_PREFIX = "/"

class GameChatAction(
  private val lookingForGameReporter: TwitterBroadcaster,
  private val registry: CommandRegistry,
) : V086Action<GameChatRequest>, V086GameEventHandler<GameChatEvent> {
  override fun toString() = "GameChatAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: GameChatRequest, clientHandler: V086ClientHandler) {
    if (clientHandler.user.game == null) return
    if (!message.message.startsWith(COMMAND_PREFIX)) {
      try {
        clientHandler.user.gameChat(message.message, message.messageNumber)
      } catch (e: GameChatException) {
        logger.atSevere().withCause(e).log("Failed to send game chat message")
      }
      return
    }

    val user = clientHandler.user
    val game = checkNotNull(user.game)

    // Flood control for non-elevated users
    if (user.accessLevel < AccessManager.ACCESS_ELEVATED) {
      try {
        user.chat(":USER_COMMAND")
      } catch (e: ActionException) {
        game.announce("Denied: Flood Control", user)
        return
      }
    }

    val text = message.message

    // Try game-owner context first (owner or superadmin)
    if (user === game.owner || user.accessLevel >= AccessManager.ACCESS_SUPERADMIN) {
      val ownerCmd = registry.find(text, CommandContext.GAME_OWNER)
      if (ownerCmd != null && user.accessLevel >= ownerCmd.minimumAccessLevel) {
        val ctx =
          CommandExecutionContext(
            user = user,
            server = user.server,
            clientHandler = clientHandler,
            registry = registry,
            currentContext = CommandContext.GAME_OWNER,
            game = game,
          )
        ownerCmd.execute(text, ctx)
        return
      }
    }

    // Then try game-chat context
    val gameCmd = registry.find(text, CommandContext.GAME_CHAT)
    if (gameCmd != null && user.accessLevel >= gameCmd.minimumAccessLevel) {
      val ctx =
        CommandExecutionContext(
          user = user,
          server = user.server,
          clientHandler = clientHandler,
          registry = registry,
          currentContext = CommandContext.GAME_CHAT,
          game = game,
        )
      gameCmd.execute(text, ctx)
      return
    }

    game.announce(EmuLang.getString("ChatAction.UnrecognizedCommand", text), user)
  }

  override fun handleEvent(gameChatEvent: GameChatEvent, clientHandler: V086ClientHandler) {
    try {
      if (
        clientHandler.user.searchIgnoredUsers(
          gameChatEvent.user.connectSocketAddress.address.hostAddress
        )
      )
        return
      if (clientHandler.user.ignoreAll) {
        if (
          gameChatEvent.user.accessLevel < AccessManager.ACCESS_ADMIN &&
            gameChatEvent.user !== clientHandler.user
        )
          return
      }
      clientHandler.send(GameChatNotification(0, gameChatEvent.user.name!!, gameChatEvent.message))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct GameChat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
