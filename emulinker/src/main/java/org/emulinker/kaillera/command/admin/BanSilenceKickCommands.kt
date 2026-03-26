package org.emulinker.kaillera.command.admin

import java.util.Scanner
import kotlin.time.Duration.Companion.minutes
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang

/** `/ban <UserID> <minutes> [reason]` — temporarily ban a user. */
object BanCommand : ServerCommand {
  override val name = "/ban"
  override val usage = "/ban <UserID> <minutes> [reason]"
  override val description = "Temporarily ban a user. Reason is internal only."
  override val minimumAccessLevel = AccessManager.ACCESS_MODERATOR
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val mins = scanner.nextInt()
      val reason =
        if (scanner.hasNext())
          buildString { while (scanner.hasNext()) append(scanner.next()).append(" ") }.trim()
        else null

      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotBanSelf"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotBanAdmin"))
        return
      }
      ctx.server.announce(
        EmuLang.getString("AdminCommandAction.Banned", mins, user.name),
        false,
        null,
      )
      user.quit(EmuLang.getString("AdminCommandAction.QuitBanned"))
      ctx.server.accessManager.addTempBan(
        user.connectSocketAddress.address.hostAddress,
        mins.minutes,
        ctx.user.name,
        reason,
      )
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.BanError"))
    }
  }
}

/** `/silence <UserID> <minutes> [reason]` — temporarily silence a user. */
object SilenceCommand : ServerCommand {
  override val name = "/silence"
  override val usage = "/silence <UserID> <minutes> [reason]"
  override val description = "Temporarily silence a user. Reason is internal only."
  override val minimumAccessLevel = AccessManager.ACCESS_MODERATOR
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val mins = scanner.nextInt()
      val reason =
        if (scanner.hasNext())
          buildString { while (scanner.hasNext()) append(scanner.next()).append(" ") }.trim()
        else null

      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotSilenceSelf"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotSilenceAdmin"))
        return
      }
      if (
        access == AccessManager.ACCESS_MODERATOR &&
          ctx.user.accessLevel == AccessManager.ACCESS_MODERATOR
      ) {
        ctx.sendInfo("You cannot silence a moderator if you're not an admin!")
        return
      }
      if (ctx.user.accessLevel == AccessManager.ACCESS_MODERATOR) {
        if (ctx.server.accessManager.isSilenced(user.socketAddress!!.address)) {
          ctx.sendInfo("This User has already been Silenced. Please wait until his time expires.")
          return
        }
        if (mins > 15) {
          ctx.sendInfo("Moderators can only silence up to 15 minutes!")
          return
        }
      }
      ctx.server.accessManager.addSilenced(
        user.connectSocketAddress.address.hostAddress,
        mins.minutes,
        ctx.user.name,
        reason,
      )
      ctx.server.announce(
        EmuLang.getString("AdminCommandAction.Silenced", mins, user.name),
        false,
        null,
      )
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.SilenceError"))
    }
  }
}

/** `/kick <UserID>` — kick a user from the server. */
object ServerKickCommand : ServerCommand {
  override val name = "/kick"
  override val usage = "/kick <UserID>"
  override val description = "Kick a user from the server"
  override val minimumAccessLevel = AccessManager.ACCESS_MODERATOR
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotKickSelf"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (
        access == AccessManager.ACCESS_MODERATOR &&
          ctx.user.accessLevel == AccessManager.ACCESS_MODERATOR
      ) {
        ctx.sendInfo("You cannot kick a moderator if you're not an admin!")
        return
      }
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotKickAdmin"))
        return
      }
      user.quit(EmuLang.getString("AdminCommandAction.QuitKicked"))
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.KickError"))
    }
  }
}
