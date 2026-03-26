package org.emulinker.kaillera.command.admin

import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand
import org.emulinker.kaillera.model.impl.Trivia

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
          server.announce("<Trivia> SupraTrivia has been reset!", false, null)
          val t = Trivia(server)
          val th = Thread(t)
          th.start()
          server.triviaThread = th
          server.trivia = t
          t.setTriviaPaused(false)
        }
        args == "/triviaon" -> {
          if (server.switchTrivia) {
            ctx.sendInfo("Trivia already started!")
            return
          }
          server.announce("SupraTrivia has been started!", false, null)
          val t = Trivia(server)
          val th = Thread(t)
          th.start()
          server.triviaThread = th
          server.trivia = t
          t.setTriviaPaused(false)
        }
        args == "/triviaoff" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.announce("SupraTrivia has been stopped!", false, null)
          server.trivia!!.saveScores(false)
          server.triviaThread!!.stop()
          server.switchTrivia = false
          server.trivia = null
        }
        args == "/triviapause" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.trivia!!.setTriviaPaused(true)
          server.announce("<Trivia> SupraTrivia will be paused after this question!", false, null)
        }
        args == "/triviaresume" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.trivia!!.setTriviaPaused(false)
          server.announce("<Trivia> SupraTrivia has been resumed!", false, null)
        }
        args == "/triviasave" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.trivia!!.saveScores(true)
        }
        args == "/triviascores" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.trivia!!.displayHighScores(false)
        }
        args == "/triviawin" -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          server.trivia!!.displayHighScores(true)
        }
        args.startsWith("/triviaupdate") -> {
          if (server.trivia == null) {
            ctx.sendInfo("Trivia needs to be started first!")
            return
          }
          val sc = Scanner(args).useDelimiter(" ")
          sc.next()
          val ip = sc.next()
          val newIp = sc.next()
          if (server.trivia!!.updateIP(ip, newIp)) {
            server.announce("<Trivia> ${newIp.take(4)}.... Trivia IP was updated!", false, admin)
          } else {
            server.announce(
              "<Trivia> ${ip.take(4)} was not found! Error updating score!",
              false,
              admin,
            )
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
          server.announce(
            "<Trivia> SupraTrivia's question delay has been changed to ${secs}s!",
            false,
            admin,
          )
        }
        else -> ctx.sendInfo("Unknown trivia command.")
      }
    } catch (e: Exception) {
      ctx.sendInfo("Trivia command error: ${e.message}")
    }
  }
}
