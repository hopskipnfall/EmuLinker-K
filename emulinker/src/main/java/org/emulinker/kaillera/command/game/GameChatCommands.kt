package org.emulinker.kaillera.command.game

import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.DurationUnit.SECONDS
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.proto.GameLog
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuLang.getStringOrNull
import org.emulinker.util.EmuUtil.min
import org.emulinker.util.EmuUtil.toLocalizedString
import org.emulinker.util.EmuUtil.toMillisDouble
import org.emulinker.util.EmuUtil.toSecondDoublePrecisionString

/** `/p2pon` / `/p2poff` — toggle server activity filtering during gameplay. */
object P2PCommand : ServerCommand {
  override val name = "/p2p"
  override val usage = "/p2pon | /p2poff"
  override val description = "Ignore or restore server activity during gameplay"
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun matches(rawMessage: String) = rawMessage == "/p2pon" || rawMessage == "/p2poff"

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    when (args) {
      "/p2pon" -> {
        if (game.owner == ctx.user) {
          game.ignoringUnnecessaryServerActivity = true
          game.players.forEach { u ->
            if (u.loggedIn) {
              u.ignoringUnnecessaryServerActivity = true
              u.game?.announce("This game will NOT receive any server activity during gameplay!", u)
            }
          }
        } else {
          ctx.user.ignoringUnnecessaryServerActivity = true
          game.players.forEach { u ->
            if (u.loggedIn)
              u.game?.announce(
                "${ctx.user.name} will NOT receive any server activity during gameplay!",
                u,
              )
          }
        }
      }
      "/p2poff" -> {
        if (game.owner == ctx.user) {
          game.ignoringUnnecessaryServerActivity = false
          game.players.forEach { u ->
            if (u.loggedIn) {
              u.ignoringUnnecessaryServerActivity = false
              u.game?.announce("This game will NOW receive ALL server activity during gameplay!", u)
            }
          }
        } else {
          ctx.user.ignoringUnnecessaryServerActivity = false
          game.players.forEach { u ->
            if (u.loggedIn)
              u.game?.announce(
                "${ctx.user.name} will NOW receive ALL server activity during gameplay!",
                u,
              )
          }
        }
      }
      else -> ctx.announceGame("Failed P2P: /p2pon or /p2poff", ctx.user)
    }
  }
}

/**
 * `/lagstat` — display per-player lag statistics.
 *
 * Injected with [RuntimeFlags] and [Clock] via constructor because it's a class, not an object.
 */
class LagstatCommand(private val flags: RuntimeFlags, private val clock: Clock) : ServerCommand {
  override val name = "/lagstat"
  override val usage = "/lagstat"
  override val description = "Show lag statistics for the current game"
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    game.chat(ctx.user, "/lagstat")
    val lagometer = game.lagometer ?: return

    val lagstatDuration = min(flags.lagstatDuration, clock.now() - lagometer.lastLagReset)
    val lagstatDurationStr =
      if (lagstatDuration < 1.minutes) lagstatDuration.toLocalizedString(SECONDS)
      else lagstatDuration.toLocalizedString(MINUTES, 1)
    game.announce(
      EmuLang.getString(
        "Lagstat.TotalGameLagSummary",
        lagstatDurationStr,
        game.currentGameLag.toSecondDoublePrecisionString(),
      )
    )
    val lagPerPlayer = lagometer.gameLagPerPlayer
    game.announce(
      EmuLang.getString("Lagstat.PerUserSummary") +
        " " +
        game.players
          .filter { !it.inStealthMode }
          .joinToString(", ") {
            "P${it.playerNumber}: ${lagPerPlayer[it.playerNumber - 1].toSecondDoublePrecisionString()}"
          }
    )
    val p1 = game.players.firstOrNull()
    if (p1 != null && p1.connectionType == ConnectionType.LAN && lagstatDuration > 10.seconds) {
      val lagPerDuration = game.currentGameLag / lagstatDuration
      val playerToLag = game.players.associateWith { lagPerPlayer[it.playerNumber - 1] }
      if (lagPerDuration > 0.3.seconds / 1.minutes) {
        val (laggiestPlayer, lagValue) = playerToLag.maxBy { (_, lag) -> lag }
        if (lagValue > Duration.ZERO) {
          val targetFrameDelay = laggiestPlayer.frameDelay + 1
          fun pingThresholdForDelay(delay: Int, ct: ConnectionType) =
            ((delay - 1.0) * ct.byteValue.toInt() / KailleraGame.GAME_FPS).seconds
          val suggestedFakePing =
            arrayOf(
                pingThresholdForDelay(targetFrameDelay, laggiestPlayer.connectionType),
                pingThresholdForDelay(targetFrameDelay + 1, laggiestPlayer.connectionType),
              )
              .map { it.toMillisDouble() }
              .average()
              .let { (it).roundToInt() }
          game.announce(
            EmuLang.getString(
              "Lagstat.LagReductionRecommendation",
              laggiestPlayer.name,
              targetFrameDelay,
              suggestedFakePing,
            )
          )
        }
      } else game.announce(EmuLang.getString("Lagstat.GameNotLaggy"))
    }
  }
}

/** `/lagreset` — reset lag statistics. */
object LagResetCommand : ServerCommand {
  override val name = "/lagreset"
  override val usage = "/lagreset"
  override val description = "Reset lag statistics"
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    game.chat(ctx.user, "/lagreset")
    game.resetLag()
    game.announce(EmuLang.getString("Lagstat.LagstatReset"))
  }
}

/** `/fps <value>` — set a custom FPS override for the game (range 0.1–100). */
object FpsCommand : ServerCommand {
  override val name = "/fps"
  override val usage = "/fps <value>"
  override val description = "Set a custom FPS override (0.1–100)"
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun matches(rawMessage: String): Boolean {
    val rest = rawMessage.removePrefix("/fps ").toDoubleOrNull() ?: return false
    return rest in 0.1..100.0
  }

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    game.chat(ctx.user, args)
    val newFps = args.removePrefix("/fps ").toDouble()
    game.setGameFps(newFps)
    game.announce(EmuLang.getString("Fps.NewFpsMeasurement", newFps))
  }
}

/** `/stop` — cancel a pending Looking-for-Game tweet. */
class StopCommand(private val lookingForGameReporter: TwitterBroadcaster) : ServerCommand {
  override val name = "/stop"
  override val usage = "/stop"
  override val description = "Cancel a pending tweet from Looking For Game"
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    if (lookingForGameReporter.cancelActionsForUser(ctx.user.id)) {
      game.announce(
        getStringOrNull("KailleraServerImpl.CanceledPendingTweet") ?: "Canceled pending tweet.",
        ctx.user,
      )
    } else {
      game.announce("No pending tweets.", ctx.user)
    }
  }
}

/** `/loggame` — enable game-session logging (admin only). */
object LogGameCommand : ServerCommand {
  override val name = "/loggame"
  override val usage = "/loggame"
  override val description = "Enable session logging for this game"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.GAME_CHAT)

  override fun matches(rawMessage: String) = rawMessage.trim() == "/loggame"

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val game = ctx.game ?: return
    game.chat(ctx.user, args)
    if (game.gameLogBuilder == null) {
      game.gameLogBuilder = GameLog.newBuilder()
      game.announce("Enabled logging for session.")
    }
  }
}
