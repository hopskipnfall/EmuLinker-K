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
      ctx.announceGame(
        EmuLang.getString("GameOwner.StartError", e.message ?: "Unknown Error"),
        ctx.user,
      )
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
        game.announce(EmuLang.getString("GameOwner.StartNCount", n))
      } else ctx.announceGame(EmuLang.getString("GameOwner.StartNUsage"), ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.StartNError"), ctx.user)
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
        game.announce(EmuLang.getString("GameOwner.AllKicked"))
        return
      }
      val num = token.removePrefix("/kick").trim().toIntOrNull() ?: sc.nextInt()
      if (num in 1..100) {
        val p = game.getPlayer(num)
        if (p != null) game.kick(ctx.user, p.id)
        else ctx.announceGame(EmuLang.getString("GameOwner.PlayerNotFound"), ctx.user)
      } else ctx.announceGame(EmuLang.getString("GameOwner.KickUsage"), ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.KickError"), ctx.user)
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
        game.announce(EmuLang.getString("GameOwner.AllMuted"))
        return
      }
      val userID = sc.nextInt()
      val user =
        ctx.clientHandler.user.server.getUser(userID)
          ?: run {
            ctx.announceGame(EmuLang.getString("GameOwner.PlayerNotFound"), ctx.user)
            return
          }
      if (user === ctx.user) {
        ctx.announceGame(EmuLang.getString("GameOwner.CantMuteSelf"), ctx.user)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.announceGame(EmuLang.getString("GameOwner.CantMuteAdmin"), ctx.user)
        return
      }
      game.mutedUsers.add(user.connectSocketAddress.address.hostAddress)
      user.isMuted = true
      game.announce(EmuLang.getString("GameOwner.UserMuted", user.name))
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.MuteUsage"), ctx.user)
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
        game.announce(EmuLang.getString("GameOwner.AllUnmuted"))
        return
      }
      val userID = sc.nextInt()
      val user =
        ctx.clientHandler.user.server.getUser(userID)
          ?: run {
            ctx.announceGame(EmuLang.getString("GameOwner.PlayerNotFound"), ctx.user)
            return
          }
      if (user === ctx.user) {
        ctx.announceGame(EmuLang.getString("GameOwner.CantUnmuteSelf"), ctx.user)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          ctx.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        ctx.announceGame(EmuLang.getString("GameOwner.CantUnmuteAdmin"), ctx.user)
        return
      }
      game.mutedUsers.remove(user.connectSocketAddress.address.hostAddress)
      user.isMuted = false
      game.announce(EmuLang.getString("GameOwner.UserUnmuted", user.name))
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.UnmuteUsage"), ctx.user)
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
        ctx.announceGame(EmuLang.getString("GameOwner.SwapLimit"), ctx.user)
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
            game.announce(EmuLang.getString("GameOwner.UserSwapped", p.name, p.playerNumber))
          }
        } else ctx.announceGame(EmuLang.getString("GameOwner.SwapError"), ctx.user)
      }
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.SwapUsage"), ctx.user)
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
      game.announce(EmuLang.getString("GameOwner.MaxUsersSpam"), ctx.user)
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
        game.announce(EmuLang.getString("GameOwner.MaxUsersSet", n))
      } else ctx.announceGame(EmuLang.getString("GameOwner.MaxUsersUsage"), ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.MaxUsersError"), ctx.user)
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
        game.announce(EmuLang.getString("GameOwner.MaxPingSet", n))
      } else ctx.announceGame(EmuLang.getString("GameOwner.MaxPingUsage"), ctx.user)
    } catch (e: NoSuchElementException) {
      ctx.announceGame(EmuLang.getString("GameOwner.MaxPingError"), ctx.user)
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
    game.announce(EmuLang.getString("GameOwner.EmuRestricted", emu))
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
    game.announce(EmuLang.getString("GameOwner.ConnRestricted", conn))
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
      game.announce(EmuLang.getString("GameOwner.SameDelayOn"))
    } else {
      game.sameDelay = false
      game.announce(EmuLang.getString("GameOwner.SameDelayOff"))
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
    game.announce(EmuLang.getString("GameOwner.NumPlayers", game.players.size), ctx.user)
  }
}
