package org.emulinker.kaillera.command.game_owner

import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.util.EmuLang

/** `/start` — start the game immediately. */
object StartCommand : ServerCommand {
  override val name = "/start"
  override val usage = "/start"
  override val description = "Start the game immediately"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  // Exact match only — /startn is handled separately.
  override fun matches(rawMessage: String) = rawMessage == "/start"

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    try {
      game.start(ctx.user)
    } catch (e: Exception) {
      ctx.announceGame("Start Error: ${e.message}", ctx.user)
    }
  }
}

/** `/startn <count>` — start when n players have joined. */
object StartNCommand : ServerCommand {
  override val name = "/startn"
  override val usage = "/startn <count>"
  override val description = "Start the game when n players have joined"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      sc.next()
      val n = sc.nextInt()
      if (n in 1..100) {
        game.startN = n
        game.announce("This game will start when $n players have joined.")
      } else ctx.announceGame("StartN Error: Enter value between 1 and 100.", ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Failed: /startn <#>", ctx.user)
    }
  }
}

/** `/kick <Player#>` or `/kickall` — kick player(s) from the game room. */
object GameKickCommand : ServerCommand {
  override val name = "/kick"
  override val usage = "/kick <Player#> | /kickall"
  override val description = "Kick a player or all players from the room"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      val token = sc.next()
      if (token == "/kickall") {
        for (w in game.players.size downTo 1) {
          val p = game.getPlayer(w) ?: continue
          if (p.accessLevel < AccessManager.ACCESS_ADMIN && p != game.owner)
            game.kick(ctx.user, p.id)
        }
        game.announce("All players have been kicked!")
        return
      }
      val num = token.removePrefix("/kick").trim().toIntOrNull() ?: sc.nextInt()
      if (num in 1..100) {
        val p = game.getPlayer(num)
        if (p != null) game.kick(ctx.user, p.id)
        else ctx.announceGame("Player doesn't exist!", ctx.user)
      } else ctx.announceGame("Kick Player Error: Enter value between 1 and 100", ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Failed: /kick <Player#> or /kickall to kick all players.", ctx.user)
    }
  }
}

/** `/mute <UserID>` or `/muteall` — mute player(s). */
object MuteCommand : ServerCommand {
  override val name = "/mute"
  override val usage = "/mute <UserID> | /muteall"
  override val description = "Mute a player or all players"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      val token = sc.next()
      if (token == "/muteall") {
        for (w in 1..game.players.size) {
          val p = game.getPlayer(w) ?: continue
          if (p.accessLevel < AccessManager.ACCESS_ADMIN && p != game.owner) {
            p.isMuted = true
            game.mutedUsers.add(p.connectSocketAddress.address.hostAddress)
          }
        }
        game.announce("All players have been muted!")
        return
      }
      val userID = sc.nextInt()
      val user =
        ctx.clientHandler.user.server.getUser(userID)
          ?: run {
            ctx.announceGame("Player doesn't exist!", ctx.user)
            return
          }
      if (user === ctx.user) {
        ctx.announceGame("You can't mute yourself!", ctx.user)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.announceGame("You can't mute an Admin", ctx.user)
        return
      }
      game.mutedUsers.add(user.connectSocketAddress.address.hostAddress)
      user.isMuted = true
      game.announce("${user.name} has been muted!")
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Mute Player Error: /mute <UserID>", ctx.user)
    }
  }
}

/** `/unmute <UserID>` or `/unmuteall` — unmute player(s). */
object UnmuteCommand : ServerCommand {
  override val name = "/unmute"
  override val usage = "/unmute <UserID> | /unmuteall"
  override val description = "Unmute a player or all players"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      val token = sc.next()
      if (token == "/unmuteall") {
        game.players.forEach { u ->
          u.isMuted = false
          game.mutedUsers.remove(u.connectSocketAddress.address.hostAddress)
        }
        game.announce("All players have been unmuted!")
        return
      }
      val userID = sc.nextInt()
      val user =
        ctx.clientHandler.user.server.getUser(userID)
          ?: run {
            ctx.announceGame("Player doesn't exist!", ctx.user)
            return
          }
      if (user === ctx.user) {
        ctx.announceGame("You can't unmute yourself!", ctx.user)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.announceGame("You can't unmute an Admin", ctx.user)
        return
      }
      game.mutedUsers.remove(user.connectSocketAddress.address.hostAddress)
      user.isMuted = false
      game.announce("${user.name} has been unmuted!")
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Unmute Player Error: /unmute <UserID>", ctx.user)
    }
  }
}

/** `/swap <order>` — reorder player slots (e.g. `/swap 213`). */
object SwapCommand : ServerCommand {
  override val name = "/swap"
  override val usage = "/swap <order>"
  override val description = "Swap player slots, e.g. /swap 213 for a 3-player game"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      sc.next()
      val test = sc.nextInt()
      val str = test.toString()
      if (game.players.size < str.length) {
        ctx.announceGame("Failed: You can't swap more than the # of players in the room.", ctx.user)
        return
      }
      if (test > 0) {
        val num = IntArray(str.length) { str[it].toString().toInt() }
        var numCount = 0
        for (i in num.indices) {
          numCount = 1
          if (num[i] == 0 || num[i] > game.players.size) break
          for (j in num.indices) if (num[i] != num[j]) numCount++
        }
        if (numCount == game.players.size) {
          game.swap = true
          for (i in str.indices) {
            val p = game.players[i]
            p.playerNumber = num[i]
            game.announce("${p.name} is now Player#: ${p.playerNumber}")
          }
        } else
          ctx.announceGame(
            "Swap Player Error: /swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
            ctx.user,
          )
      }
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Swap Player Error: /swap <order> eg. 123..n", ctx.user)
    }
  }
}

/** `/maxusers <n>` — set the maximum number of users in the game. */
object MaxUsersCommand : ServerCommand {
  override val name = "/maxusers"
  override val usage = "/maxusers <n>"
  override val description = "Set the maximum players in the room"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  private var lastChange = 0L

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    if (System.currentTimeMillis() - lastChange <= 3000) {
      game.announce("Max User Command Spam Detection...Please Wait!", ctx.user)
      lastChange = System.currentTimeMillis()
      return
    }
    lastChange = System.currentTimeMillis()
    val sc = Scanner(args).useDelimiter(" ")
    try {
      sc.next()
      val n = sc.nextInt()
      if (n in 1..100) {
        game.maxUsers = n
        game.announce("Max Users has been set to $n")
      } else ctx.announceGame("Max Users Error: Enter value between 1 and 100", ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Failed: /maxusers <#>", ctx.user)
    }
  }
}

/** `/maxping <ms>` — set the maximum ping threshold for joining players. */
object MaxPingCommand : ServerCommand {
  override val name = "/maxping"
  override val usage = "/maxping <ms>"
  override val description = "Set the maximum allowed ping (1–1000 ms)"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val sc = Scanner(args).useDelimiter(" ")
    try {
      sc.next()
      val n = sc.nextInt()
      if (n in 1..1000) {
        game.maxPing = n
        game.announce("Max Ping has been set to $n")
      } else ctx.announceGame("Max Ping Error: Enter value between 1 and 1000", ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame("Failed: /maxping <#>", ctx.user)
    }
  }
}

/** `/setemu [any]` — restrict the game room to the owner's emulator type. */
object SetEmuCommand : ServerCommand {
  override val name = "/setemu"
  override val usage = "/setemu [any]"
  override val description = "Restrict room to this emulator (or 'any' to allow all)"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val emu = if (args == "/setemu any") "any" else game.owner.clientType ?: "any"
    game.aEmulator = emu
    game.announce("Owner has restricted the emulator to: $emu")
  }
}

/** `/setconn [any]` — restrict the game room to the owner's connection type. */
object SetConnCommand : ServerCommand {
  override val name = "/setconn"
  override val usage = "/setconn [any]"
  override val description = "Restrict room to this connection type (or 'any')"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    val conn = if (args == "/setconn any") "any" else game.owner.connectionType.readableName
    game.aConnection = conn
    game.announce("Owner has restricted the connection type to: $conn")
  }
}

/** `/detectautofire <0-5>` — configure autofire detection sensitivity. */
object DetectAutoFireCommand : ServerCommand {
  override val name = "/detectautofire"
  override val usage = "/detectautofire <0–5>"
  override val description = "Set autofire detection sensitivity (0 = disabled)"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    if (game.status != GameStatus.WAITING) {
      ctx.announceGame(
        EmuLang.getString("GameOwnerCommandAction.AutoFireChangeDeniedInGame"),
        ctx.user,
      )
      return
    }
    val sc = Scanner(args).useDelimiter(" ")
    try {
      sc.next()
      val s = sc.nextInt()
      if (s in 0..5) {
        game.autoFireDetector!!.sensitivity = s
        game.announce(
          EmuLang.getString("GameOwnerCommandAction.HelpCurrentSensitivity", s) +
            if (s == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled") else ""
        )
      } else autoFireHelp(ctx)
    } catch (e: NoSuchElementException) {
      autoFireHelp(ctx)
    }
  }

  private fun autoFireHelp(ctx: CommandExecutionContext) {
    val cur = ctx.game?.autoFireDetector?.sensitivity ?: 0
    ctx.announceGame(EmuLang.getString("GameOwnerCommandAction.HelpSensitivity"), ctx.user)
    ctx.announceGame(EmuLang.getString("GameOwnerCommandAction.HelpDisable"), ctx.user)
    ctx.announceGame(
      EmuLang.getString("GameOwnerCommandAction.HelpCurrentSensitivity", cur) +
        if (cur == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled") else "",
      ctx.user,
    )
  }
}

/** `/samedelay true|false` — enable/disable same-delay mode. */
object SameDelayCommand : ServerCommand {
  override val name = "/samedelay"
  override val usage = "/samedelay true | false"
  override val description = "Play at the same delay as the player with the highest ping"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    if (args == "/samedelay true") {
      game.sameDelay = true
      game.announce("Players will have the same delay when game starts (restarts)!")
    } else {
      game.sameDelay = false
      game.announce("Players will have independent delays when game starts (restarts)!")
    }
  }
}

/** `/num` — show how many players are in the room. */
object NumCommand : ServerCommand {
  override val name = "/num"
  override val usage = "/num"
  override val description = "Show the number of players in the room"
  override val contexts = setOf(CommandContext.GAME_OWNER)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    game.announce("${game.players.size} in the room!", ctx.user)
  }
}
