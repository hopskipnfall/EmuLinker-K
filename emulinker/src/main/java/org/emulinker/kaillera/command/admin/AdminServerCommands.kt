package org.emulinker.kaillera.command.admin

import java.util.Locale
import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.toSimpleUtcDatetime
import org.emulinker.util.WildcardStringPattern

/** `/announce <message>` — announce to all lobby users. `/announceall` includes game rooms. */
object AnnounceCommand : ServerCommand {
  override val name = "/announce"
  override val usage = "/announce <message>  (or /announceall for all rooms)"
  override val description = "Broadcast an announcement to the server"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun matches(rawMessage: String) =
    rawMessage.startsWith("/announce ") || rawMessage.startsWith("/announceall ")

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.AnnounceError"))
      return
    }
    val all = args.startsWith("/announceall")
    var text = args.substring(space + 1)
    if (text.startsWith(":")) text = text.substring(1)
    ctx.server.announce(text, all, null)
  }
}

/** `/announcegame <GameID> <message>` — announce to a specific game room. */
object AnnounceGameCommand : ServerCommand {
  override val name = "/announcegame"
  override val usage = "/announcegame <GameID> <message>"
  override val description = "Announce a message to a specific game room"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val gameID = scanner.nextInt()
      val text = buildString { while (scanner.hasNext()) append(scanner.next()).append(" ") }
      val game =
        ctx.server.getGame(gameID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.GameNotFound", gameID))
            return
          }
      game.announce(text)
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.AnnounceGameError"))
    }
  }
}

/** `/findgame <pattern>` — search game rooms by wildcard name pattern. */
object FindGameCommand : ServerCommand {
  override val name = "/findgame"
  override val usage = "/findgame <pattern>"
  override val description = "Find a game room by name pattern"
  override val minimumAccessLevel = AccessManager.ACCESS_MODERATOR
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.FindGameError"))
      return
    }
    val pattern = WildcardStringPattern(args.substring(space + 1))
    var found = 0
    for (game in ctx.server.gamesMap.values) {
      if (pattern.match(game.romName)) {
        ctx.sendInfo("GameID: ${game.id}, Owner: <${game.owner.name}>, Game: ${game.romName}")
        found++
      }
    }
    if (found == 0) ctx.sendInfo(EmuLang.getString("AdminCommandAction.NoGamesFound"))
  }
}

/** `/finduser <nick>` — admin version: includes IP address in results. */
object AdminFindUserCommand : ServerCommand {
  override val name = "/finduser"
  override val usage = "/finduser <nick>"
  override val description = "Find a user by partial name (shows IP)"
  override val minimumAccessLevel = AccessManager.ACCESS_MODERATOR
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val space = args.indexOf(' ')
    if (space < 0) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.FindUserError"))
      return
    }
    val query = args.substring(space + 1).lowercase(Locale.getDefault())
    var found = 0
    for (user in ctx.server.usersMap.values) {
      if (!user.loggedIn) continue
      if (!user.name!!.lowercase(Locale.getDefault()).contains(query)) continue
      var msg =
        "UserID: ${user.id}, IP: ${user.connectSocketAddress.address.hostAddress}, Nick: <${user.name}>, Access: ${user.accessStr}"
      if (user.game != null) msg += ", GameID: ${user.game!!.id}, Game: ${user.game!!.romName}"
      ctx.sendInfo(msg)
      found++
    }
    if (found == 0) ctx.sendInfo(EmuLang.getString("AdminCommandAction.NoUsersFound"))
  }
}

/** `/closegame <GameID>` — force-close a game room. */
object CloseGameCommand : ServerCommand {
  override val name = "/closegame"
  override val usage = "/closegame <GameID>"
  override val description = "Force-close a game room"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next()
      val gameID = scanner.nextInt()
      val game =
        ctx.server.getGame(gameID)
          ?: run {
            ctx.sendInfo(EmuLang.getString("AdminCommandAction.GameNotFound", gameID))
            return
          }
      val access = ctx.server.accessManager.getAccess(game.owner.connectSocketAddress.address)
      if (
        access >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN &&
          game.owner.loggedIn
      ) {
        ctx.sendInfo(EmuLang.getString("AdminCommandAction.CanNotCloseAdminGame"))
        return
      }
      game.owner.quitGame()
    } catch (e: NoSuchElementException) {
      ctx.sendInfo(EmuLang.getString("AdminCommandAction.CloseGameError"))
    }
  }
}

/** `/version` — admin version: shows full server info including JVM stats. */
object AdminVersionCommand : ServerCommand {
  override val name = "/version"
  override val usage = "/version"
  override val description = "Show server version and system info"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val info = ctx.server.releaseInfo
    ctx.sendInfo(
      "VERSION: ${info.productName}: ${info.version}: ${info.buildDate.toSimpleUtcDatetime()}"
    )
    val props = System.getProperties()
    ctx.sendInfo("JAVAVER: ${props.getProperty("java.version")}")
    ctx.sendInfo("JAVAVEND: ${props.getProperty("java.vendor")}")
    ctx.sendInfo("OSNAME: ${props.getProperty("os.name")}")
    ctx.sendInfo("OSARCH: ${props.getProperty("os.arch")}")
    ctx.sendInfo("OSVER: ${props.getProperty("os.version")}")
    val runtime = Runtime.getRuntime()
    ctx.sendInfo("NUMPROCS: ${runtime.availableProcessors()}")
    ctx.sendInfo("FREEMEM: ${runtime.freeMemory()}")
    ctx.sendInfo("MAXMEM: ${runtime.maxMemory()}")
    ctx.sendInfo("TOTMEM: ${runtime.totalMemory()}")
    val env = System.getenv()
    if (EmuUtil.systemIsWindows()) {
      ctx.sendInfo("COMPNAME: ${env["COMPUTERNAME"]}")
      ctx.sendInfo("USER: ${env["USERNAME"]}")
    } else {
      ctx.sendInfo("COMPNAME: ${env["HOSTNAME"]}")
      ctx.sendInfo("USER: ${env["USERNAME"]}")
    }
  }
}
