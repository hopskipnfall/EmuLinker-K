package org.emulinker.kaillera.command.shared

import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand

/** Generates `/help` text from the [CommandRegistry] filtered for the caller's access level. */
object HelpCommand : ServerCommand {
  override val name = "/help"
  override val usage = "/help"
  override val description = "Show available commands"
  override val contexts =
    setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT, CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val lines = ctx.registry.helpLines(ctx.currentContext, ctx.user.accessLevel)
    lines.forEach { line ->
      when (ctx.currentContext) {
        CommandContext.SERVER_LOBBY -> ctx.sendInfo(line)
        CommandContext.GAME_CHAT,
        CommandContext.GAME_OWNER -> ctx.announceGame(line, ctx.user)
      }
    }
  }
}
