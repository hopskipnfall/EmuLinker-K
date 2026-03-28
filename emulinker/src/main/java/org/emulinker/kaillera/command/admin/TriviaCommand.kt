package org.emulinker.kaillera.command.admin

import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.kaillera.model.impl.Trivia
import org.emulinker.util.EmuLang

/**
 * All trivia sub-commands handled under a single [ServerCommand] because they share a prefix
 * (`/trivia…`) and the dispatch logic would be awkward to split further.
 */
object TriviaCommand : ServerCommand {
  override val name = "/trivia"
  override val usage =
    "/triviaon | /triviaoff | /triviapause | /triviaresume | /triviasave | /triviascores | /triviawin | /triviaupdate <ip> <new> | /triviatime <s> | /triviareset"
  override val description = "Manage the SupraTrivia bot"
  override val minimumAccessLevel = AccessManager.ACCESS_ADMIN
  override val contexts = setOf(CommandContext.SERVER_LOBBY)

  override fun matches(rawMessage: String) = rawMessage.startsWith("/trivia")

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val server = ctx.server
    val admin = ctx.user
    try {
      when {
        args == "/triviareset" -> {
          if (server.switchTrivia) {
            server.trivia!!.saveScores(true)
            server.triviaThread!!.stop()
          }
          server.announce(EmuLang.getString("Trivia.Reset"), false, null)
          val t = Trivia(server)
          val th = Thread(t)
          th.start()
          server.triviaThread = th
          server.trivia = t
          t.setTriviaPaused(false)
        }
        args == "/triviaon" -> {
          if (server.switchTrivia) {
            ctx.sendInfo(EmuLang.getString("Trivia.AlreadyStarted"))
            return
          }
          server.announce(EmuLang.getString("Trivia.Started"), false, null)
          val t = Trivia(server)
          val th = Thread(t)
          th.start()
          server.triviaThread = th
          server.trivia = t
          t.setTriviaPaused(false)
        }
        args == "/triviaoff" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.announce(EmuLang.getString("Trivia.Stopped"), false, null)
          server.trivia!!.saveScores(false)
          server.triviaThread!!.stop()
          server.switchTrivia = false
          server.trivia = null
        }
        args == "/triviapause" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.trivia!!.setTriviaPaused(true)
          server.announce(EmuLang.getString("Trivia.Paused"), false, null)
        }
        args == "/triviaresume" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.trivia!!.setTriviaPaused(false)
          server.announce(EmuLang.getString("Trivia.Resumed"), false, null)
        }
        args == "/triviasave" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.trivia!!.saveScores(true)
        }
        args == "/triviascores" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.trivia!!.displayHighScores(false)
        }
        args == "/triviawin" -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          server.trivia!!.displayHighScores(true)
        }
        args.startsWith("/triviaupdate") -> {
          if (server.trivia == null) {
            ctx.sendInfo(EmuLang.getString("Trivia.NeedsStart"))
            return
          }
          val sc = Scanner(args).useDelimiter(" ")
          sc.next()
          val ip = sc.next()
          val newIp = sc.next()
          if (server.trivia!!.updateIP(ip, newIp)) {
            server.announce(EmuLang.getString("Trivia.IpUpdated", newIp.take(4)), false, admin)
          } else {
            server.announce(EmuLang.getString("Trivia.IpNotFound", ip.take(4)), false, admin)
          }
        }
        args.startsWith("/triviatime") -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          val sc = Scanner(args).useDelimiter(" ")
          sc.next()
          val secs = sc.nextInt()
          server.trivia!!.setQuestionTime(secs * 1000)
          server.announce(EmuLang.getString("Trivia.DelayChanged", secs), false, admin)
        }
        else -> ctx.sendInfo(EmuLang.getString("Trivia.UnknownCommand"))
      }
    } catch (e: Exception) {
      ctx.sendInfo(EmuLang.getString("Trivia.Error", e.message ?: "Unknown Error"))
    }
  }
}
