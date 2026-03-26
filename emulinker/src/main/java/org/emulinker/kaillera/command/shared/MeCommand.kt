package org.emulinker.kaillera.command.shared

import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand

/** `/me <message>` — third-person server/game announcement. */
object MeCommand : ServerCommand {
  override val name = "/me"
  override val usage = "/me <message>"
  override val description = "Make a personal emote announcement, e.g. /me is bored"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      reply(ctx, "Invalid # of Fields!")
      return
    }
    var announcement = args.substring(space + 1)
    // Protect against clients that prepend a colon (EmuLinker supraclient quirk)
    if (announcement.startsWith(":")) announcement = announcement.substring(1)

    val accessManager = ctx.server.accessManager
    if (
      accessManager.getAccess(ctx.user.socketAddress!!.address) < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(ctx.user.socketAddress!!.address)
    ) {
      reply(ctx, "You are silenced!")
      return
    }
    if (ctx.server.checkMe(ctx.user, announcement)) {
      val full = "*${ctx.user.name} $announcement"
      when (ctx.currentContext) {
        CommandContext.SERVER_LOBBY -> ctx.server.announce(full, true, ctx.user)
        CommandContext.GAME_CHAT -> ctx.game?.players?.forEach { it.game?.announce(full, it) }
        CommandContext.GAME_OWNER -> {} // not reached; GAME_OWNER not in contexts
      }
    }
  }

  private fun reply(ctx: CommandExecutionContext, msg: String) {
    when (ctx.currentContext) {
      CommandContext.SERVER_LOBBY -> ctx.sendInfo(msg)
      else -> ctx.announceGame(msg, ctx.user)
    }
  }
}
