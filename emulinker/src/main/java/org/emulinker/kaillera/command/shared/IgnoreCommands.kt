package org.emulinker.kaillera.command.shared

import java.util.Locale
import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang

/** `/ignore <UserID>` — add a user to the caller's ignore list. */
object IgnoreCommand : ServerCommand {
  override val name = "/ignore"
  override val usage = "/ignore <UserID>"
  override val description = "Ignore a user's chat messages"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val target = ctx.server.getUser(userID)
      if (target == null) {
        reply(ctx, EmuLang.getString("MsgCommand.UserNotFound"))
        return
      }
      if (target === ctx.user) {
        reply(ctx, EmuLang.getString("IgnoreCommand.CantIgnoreSelf"))
        return
      }
      if (ctx.user.findIgnoredUser(target.connectSocketAddress.address.hostAddress)) {
        reply(ctx, EmuLang.getString("IgnoreCommand.AlreadyIgnored"))
        return
      }
      if (target.accessLevel >= AccessManager.ACCESS_MODERATOR) {
        reply(ctx, EmuLang.getString("IgnoreCommand.CantIgnoreAdmin"))
        return
      }
      ctx.user.addIgnoredUser(target.connectSocketAddress.address.hostAddress)
      ctx.server.announce(
        EmuLang.getString("IgnoreCommand.NowIgnoring", ctx.user.name, target.name),
        false,
        null,
      )
    } catch (e: NoSuchElementException) {
      reply(ctx, EmuLang.getString("IgnoreCommand.Help"))
    }
  }

  private fun reply(ctx: CommandExecutionContext, msg: String) =
    if (ctx.currentContext == CommandContext.SERVER_LOBBY) ctx.sendInfo(msg)
    else ctx.announceGame(msg, ctx.user)
}

/** `/unignore <UserID>` — remove a user from the caller's ignore list. */
object UnignoreCommand : ServerCommand {
  override val name = "/unignore"
  override val usage = "/unignore <UserID>"
  override val description = "Stop ignoring a user"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val target = ctx.server.getUser(userID)
      if (target == null) {
        reply(ctx, "User Not Found!")
        return
      }
      if (!ctx.user.findIgnoredUser(target.connectSocketAddress.address.hostAddress)) {
        reply(ctx, EmuLang.getString("IgnoreCommand.NotIgnored"))
        return
      }
      if (ctx.user.removeIgnoredUser(target.connectSocketAddress.address.hostAddress, false)) {
        ctx.server.announce(
          EmuLang.getString("IgnoreCommand.NowUnignoring", ctx.user.name, target.name),
          gamesAlso = false,
        )
      } else {
        reply(ctx, EmuLang.getString("MsgCommand.UserNotFound"))
      }
    } catch (e: NoSuchElementException) {
      reply(ctx, EmuLang.getString("IgnoreCommand.UnignoreHelp"))
    }
  }

  private fun reply(ctx: CommandExecutionContext, msg: String) =
    if (ctx.currentContext == CommandContext.SERVER_LOBBY) ctx.sendInfo(msg)
    else ctx.announceGame(msg, ctx.user)
}

/** `/ignoreall` — ignore all non-admin users. */
object IgnoreAllCommand : ServerCommand {
  override val name = "/ignoreall"
  override val usage = "/ignoreall"
  override val description = "Ignore everyone's chat"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    ctx.user.ignoreAll = true
    ctx.server.announce(EmuLang.getString("IgnoreCommand.IgnoringEveryone", ctx.user.name), false)
  }
}

/** `/unignoreall` — stop ignoring all users. */
object UnignoreAllCommand : ServerCommand {
  override val name = "/unignoreall"
  override val usage = "/unignoreall"
  override val description = "Stop ignoring everyone"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    ctx.user.ignoreAll = false
    ctx.server.announce(
      EmuLang.getString("IgnoreCommand.UnignoringEveryone", ctx.user.name),
      gamesAlso = false,
    )
  }
}

/** `/finduser <nick>` — search for users by partial name (non-admin only). */
object FindUserCommand : ServerCommand {
  override val name = "/finduser"
  override val usage = "/finduser <nick>"
  override val description = "Find a user by partial name"
  // Intentionally capped at ACCESS_NORMAL so admins use the admin version which shows the IP.
  override val minimumAccessLevel = AccessManager.ACCESS_NORMAL
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo(EmuLang.getString("FindUser.Help"))
      return
    }
    val query = args.substring(space + 1).lowercase(Locale.getDefault())
    var found = 0
    for (user in ctx.user.users) {
      if (!user.loggedIn) continue
      if (!user.name!!.lowercase(Locale.getDefault()).contains(query)) continue
      val sb = StringBuilder()
      sb.append("UserID: ").append(user.id)
      sb.append(", Nick: <").append(user.name).append(">")
      sb.append(", Access: ")
      // Mask admin/superadmin access from normal users
      if (
        ctx.user.accessLevel < AccessManager.ACCESS_ADMIN &&
          (user.accessStr == "SuperAdmin" || user.accessStr == "Admin")
      ) {
        sb.append("Normal")
      } else {
        sb.append(user.accessStr)
      }
      if (user.game != null) {
        sb.append(", GameID: ").append(user.game!!.id)
        sb.append(", Game: ").append(user.game!!.romName)
      }
      ctx.sendInfo(sb.toString())
      found++
    }
    if (found == 0) ctx.sendInfo(EmuLang.getString("FindUser.NoUsersFound"))
  }
}
