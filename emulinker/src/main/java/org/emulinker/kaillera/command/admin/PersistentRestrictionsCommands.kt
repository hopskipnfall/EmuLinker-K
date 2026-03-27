package org.emulinker.kaillera.command.admin

import java.net.InetAddress
import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang

/** `/permaban <UserID> [reason]` — permanently ban a user (writes to access.cfg). */
object PermabanCommand : ServerCommand {
  override val name = "/permaban"
  override val usage = "/permaban <UserID> [reason]"
  override val description = "Permanently ban a user. Reason is internal only."
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
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
        ctx.sendInfo("Can not permaban yourself.")
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.sendInfo("Can not permaban an admin.")
        return
      }
      ctx.server.accessManager.addPermaBan(
        user.connectSocketAddress.address.hostAddress,
        ctx.user.name,
        reason,
      )
      ctx.server.announce("Admin ${ctx.user.name} permanently banned ${user.name}!", false, null)
      user.quit("You have been permanently banned.")
    } catch (e: NoSuchElementException) {
      ctx.sendInfo("Permaban Error: /permaban <UserID> [reason]")
    }
  }
}

/** `/permamute <UserID> [reason]` — permanently mute a user (writes to access.cfg). */
object PermaMuteCommand : ServerCommand {
  override val name = "/permamute"
  override val usage = "/permamute <UserID> [reason]"
  override val description = "Permanently silence a user. Reason is internal only."
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
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
        ctx.sendInfo("Can not permamute yourself.")
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.sendInfo("Can not permamute an admin.")
        return
      }
      ctx.server.accessManager.addPermaMute(
        user.connectSocketAddress.address.hostAddress,
        ctx.user.name,
        reason,
      )
      ctx.server.announce("Admin ${ctx.user.name} permanently muted ${user.name}!", false, null)
    } catch (e: NoSuchElementException) {
      ctx.sendInfo("Permamute Error: /permamute <UserID> [reason]")
    }
  }
}

/** `/clear <IP | UserID | Name>` — clear temporary restrictions for a user. */
object ClearCommand : ServerCommand {
  override val name = "/clear"
  override val usage = "/clear <IP | UserID | Name>"
  override val description = "Clear temporary bans and silences for a user"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.ClearError"))
      return
    }
    val targetStr = args.substring(space + 1).trim()

    val inetAddr: InetAddress = resolveAddress(targetStr, ctx) ?: return
    val clearAll = ctx.user.accessLevel == AccessManager.ACCESS_SUPERADMIN
    if (ctx.server.accessManager.clearTemp(inetAddr, clearAll)) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.ClearSuccess"))
    } else {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.ClearNotFound"))
    }
  }

  internal fun resolveAddress(targetStr: String, ctx: CommandExecutionContext): InetAddress? {
    val targetId = targetStr.toIntOrNull()
    if (targetId != null) {
      val user = ctx.server.getUser(targetId)
      if (user != null) return user.connectSocketAddress.address
    }

    return try {
      InetAddress.getByName(targetStr)
    } catch (e: Exception) {
      val matchedUser = ctx.server.usersMap.values.firstOrNull { it.name.equals(targetStr, ignoreCase = true) }
      if (matchedUser != null) {
        matchedUser.connectSocketAddress.address
      } else {
        ctx.sendInfo("Could not find user with ID, IP or Name: $targetStr")
        null
      }
    }
  }
}

/** `/info <IP | UserID | Name>` — show active restrictions for a user. */
object InfoCommand : ServerCommand {
  override val name = "/info"
  override val usage = "/info <IP | UserID | Name>"
  override val description = "Show active restrictions for a user (admin only)"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo("Usage: /info <IP, UserID, or Name>")
      return
    }
    val targetStr = args.substring(space + 1).trim()

    val inetAddr: InetAddress = ClearCommand.resolveAddress(targetStr, ctx) ?: return
    val tempBan = ctx.server.accessManager.getTempBan(inetAddr)
    val silence = ctx.server.accessManager.getSilence(inetAddr)
    val access = ctx.server.accessManager.getAccess(inetAddr)

    ctx.sendInfo("Info for $targetStr (${inetAddr.hostAddress}):")
    ctx.sendInfo("Access Level: ${AccessManager.ACCESS_NAMES.getOrNull(access) ?: access}")
    if (tempBan != null)
      ctx.sendInfo(
        "Active Temp Ban — Issuer: ${tempBan.issuer}, Reason: ${tempBan.reason ?: "None"}"
      )
    if (silence != null)
      ctx.sendInfo(
        "Active Silence — Issuer: ${silence.issuer}, Reason: ${silence.reason ?: "None"}"
      )
    if (tempBan == null && silence == null) ctx.sendInfo("No active temporary restrictions.")
  }
}
