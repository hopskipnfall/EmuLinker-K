package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.CommandRegistry
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.ChatNotification
import org.emulinker.kaillera.controller.v086.protocol.ChatRequest
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.model.event.ChatEvent
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.util.EmuLang

private const val COMMAND_PREFIX = "/"

class ChatAction(private val registry: CommandRegistry) :
  V086Action<ChatRequest>, V086ServerEventHandler<ChatEvent> {
  override fun toString() = "ChatAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: ChatRequest, clientHandler: V086ClientHandler) {
    val text = message.message
    if (!text.startsWith(COMMAND_PREFIX)) {
      try {
        clientHandler.user.chat(text)
      } catch (e: ActionException) {
        logger.atInfo().withCause(e).log("Chat Denied: %s: %s", clientHandler.user, text)
        try {
          clientHandler.send(
            InformationMessage(0, "server", EmuLang.getString("ChatAction.ChatDenied", e.message))
          )
        } catch (e2: MessageFormatException) {
          logger.atSevere().withCause(e2).log("Failed to construct InformationMessage")
        }
      }
      return
    }

    val user = clientHandler.user
    // Flood-control: non-elevated users have chat rate-limited via :USER_COMMAND
    if (user.accessLevel < org.emulinker.kaillera.access.AccessManager.ACCESS_ELEVATED) {
      try {
        user.chat(":USER_COMMAND")
      } catch (e: ActionException) {
        user.server.announce("Denied: Flood Control", false, user)
        return
      }
    }

    val ctx =
      CommandExecutionContext(
        user = user,
        server = user.server,
        clientHandler = clientHandler,
        registry = registry,
        currentContext = CommandContext.SERVER_LOBBY,
      )

    val cmd = registry.find(text, CommandContext.SERVER_LOBBY)
    if (cmd == null || user.accessLevel < cmd.minimumAccessLevel) {
      user.server.announce("Unknown Command: $text", false, user)
      return
    }
    cmd.execute(text, ctx)
  }

  override fun handleEvent(event: ChatEvent, clientHandler: V086ClientHandler) {
    try {
      if (
        clientHandler.user.searchIgnoredUsers(event.user.connectSocketAddress.address.hostAddress)
      )
        return
      if (clientHandler.user.ignoreAll) {
        if (
          event.user.accessLevel < org.emulinker.kaillera.access.AccessManager.ACCESS_ADMIN &&
            event.user !== clientHandler.user
        )
          return
      }
      clientHandler.send(ChatNotification(0, event.user.name!!, event.message))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct Chat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
