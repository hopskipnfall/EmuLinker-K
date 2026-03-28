package org.emulinker.kaillera.command.admin

import java.util.Scanner
import kotlin.time.Duration.Companion.minutes
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang

/** `/tempadmin <UserID> <minutes>` — grant temporary admin access (superadmin only). */
object TempAdminCommand : ServerCommand {
  override val name = "/tempadmin"
  override val usage = "/tempadmin <UserID> <minutes>"
  override val description = "Grant temporary admin access"
  override val minimumAccessLevel = AccessManager.ACCESS_SUPERADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val mins = scanner.nextInt()
      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.AlreadyAdmin"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (access >= AccessManager.ACCESS_ADMIN) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserAlreadyAdmin"))
        return
      }
      ctx.server.accessManager.addTempAdmin(
        user.connectSocketAddress.address.hostAddress,
        mins.minutes,
      )
      ctx.server.announce(
        EmuLang.getString("AdminCommandAction.TempAdminGranted", mins, user.name),
        false,
        null,
      )
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.TempAdminError"))
    }
  }
}

/** `/tempmoderator <UserID> <minutes>` — grant temporary moderator access (superadmin only). */
object TempModeratorCommand : ServerCommand {
  override val name = "/tempmoderator"
  override val usage = "/tempmoderator <UserID> <minutes>"
  override val description = "Grant temporary moderator access"
  override val minimumAccessLevel = AccessManager.ACCESS_SUPERADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val mins = scanner.nextInt()
      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.AlreadyAdmin"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (access >= AccessManager.ACCESS_ADMIN) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserAlreadyAdmin"))
        return
      }
      if (access == AccessManager.ACCESS_MODERATOR) {
        ctx.sendInfo(EmuLang.getString("TempAccess.AlreadyModerator"))
        return
      }
      ctx.server.accessManager.addTempModerator(
        user.connectSocketAddress.address.hostAddress,
        mins.minutes,
      )
      ctx.server.announce(EmuLang.getString("TempAccess.ModeratorGranted", user.name), false, null)
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("TempAccess.ModeratorError"))
    }
  }
}

/** `/tempelevated <UserID> <minutes>` — grant temporary elevated access (superadmin only). */
object TempElevatedCommand : ServerCommand {
  override val name = "/tempelevated"
  override val usage = "/tempelevated <UserID> <minutes>"
  override val description = "Grant temporary elevated access"
  override val minimumAccessLevel = AccessManager.ACCESS_SUPERADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val userID = scanner.nextInt()
      val mins = scanner.nextInt()
      val user =
        ctx.server.getUser(userID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserNotFound", userID))
            return
          }
      if (user.id == ctx.user.id) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.AlreadyAdmin"))
        return
      }
      val access = ctx.server.accessManager.getAccess(user.connectSocketAddress.address)
      if (access >= AccessManager.ACCESS_ADMIN) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.UserAlreadyAdmin"))
        return
      }
      if (access == AccessManager.ACCESS_ELEVATED) {
        ctx.sendInfo(EmuLang.getString("TempAccess.AlreadyElevated"))
        return
      }
      ctx.server.accessManager.addTempElevated(
        user.connectSocketAddress.address.hostAddress,
        mins.minutes,
      )
      ctx.server.announce(EmuLang.getString("TempAccess.ElevatedGranted", user.name), false, null)
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("TempAccess.ElevatedError"))
    }
  }
}

/** `/stealthon` / `/stealthoff` — toggle stealth mode for admins. */
object StealthCommand : ServerCommand {
  override val name = "/stealth"
  override val usage = "/stealthon | /stealthoff"
  override val description = "Toggle stealth mode (join game rooms invisibly)"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun matches(rawMessage: String) =
    rawMessage == "/stealthon" || rawMessage == "/stealthoff"

  override fun execute(args: String, ctx: CommandExecutionContext) {
    if (ctx.user.game != null) {
      ctx.sendInfo(EmuLang.getString("Stealth.NoGameRoom"))
      return
    }
    when (args) {
      "/stealthon" -> {
        ctx.user.inStealthMode = true
        ctx.sendInfo(EmuLang.getString("Stealth.On"))
      }
      "/stealthoff" -> {
        ctx.user.inStealthMode = false
        ctx.sendInfo(EmuLang.getString("Stealth.Off"))
      }
      else -> ctx.sendInfo(EmuLang.getString("Stealth.Help"))
    }
  }
}
