package org.emulinker.kaillera.command.shared

import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang

/** `/msgon` — enable incoming private messages. */
object MsgOnCommand : ServerCommand {
  override val name = "/msgon"
  override val usage = "/msgon"
  override val description = "Enable private messages"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    ctx.user.isAcceptingDirectMessages = true
    when (ctx.currentContext) {
      CommandContext.SERVER_LOBBY -> ctx.sendInfo(EmuLang.getString("MsgCommand.MessagesOn"))
      else -> ctx.announceGame(EmuLang.getString("MsgCommand.MessagesOn"), ctx.user)
    }
  }
}

/** `/msgoff` — disable incoming private messages. */
object MsgOffCommand : ServerCommand {
  override val name = "/msgoff"
  override val usage = "/msgoff"
  override val description = "Disable private messages"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    ctx.user.isAcceptingDirectMessages = false
    when (ctx.currentContext) {
      CommandContext.SERVER_LOBBY -> ctx.sendInfo(EmuLang.getString("MsgCommand.MessagesOff"))
      else -> ctx.announceGame(EmuLang.getString("MsgCommand.MessagesOff"), ctx.user)
    }
  }
}
