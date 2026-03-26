package org.emulinker.kaillera.command.shared

import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuUtil.toSimpleUtcDatetime

/** `/myip` — private message with the caller's IP address. */
object MyIpCommand : ServerCommand {
  override val name = "/myip"
  override val usage = "/myip"
  override val description = "Show your own IP address"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val ip = ctx.user.connectSocketAddress.address.hostAddress
    ctx.sendInfo("Your IP Address is: $ip")
  }
}

/** `/alivecheck` — connectivity check reply. */
object AliveCheckCommand : ServerCommand {
  override val name = "/alivecheck"
  override val usage = "/alivecheck"
  override val description = "Check that you are still connected to the server"
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    ctx.sendInfo(":ALIVECHECK=EmuLinker-K Alive Check: You are still logged in.")
  }
}

/**
 * `/version` — report the server version (shown to non-admins; admins see the full version via
 * their admin command).
 */
object VersionCommand : ServerCommand {
  override val name = "/version"
  override val usage = "/version"
  override val description = "Show the server version"
  // Visible below admin level; the admin command includes more info.
  override val minimumAccessLevel = AccessManager.ACCESS_NORMAL
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val info = ctx.server.releaseInfo
    ctx.sendInfo(
      "VERSION: ${info.productName}: ${info.version}: ${info.buildDate.toSimpleUtcDatetime()}"
    )
  }
}
