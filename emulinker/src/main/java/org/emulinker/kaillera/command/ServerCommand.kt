package org.emulinker.kaillera.command

import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.KailleraUser

/**
 * The context in which a slash command can be invoked.
 *
 * A command may be valid in more than one context (e.g. `/msg` works in both the server lobby and
 * in-game chat).
 */
enum class CommandContext {
  /** Server lobby (non-game) chat. */
  SERVER_LOBBY,

  /** In-game chat available to all players in the game. */
  GAME_CHAT,

  /** In-game chat commands only the game owner (or superadmin) can use. */
  GAME_OWNER,
}

/**
 * Contextual information passed to every [ServerCommand.execute] call, providing access to the
 * invoking user, server state, network handler, and current context.
 */
data class CommandExecutionContext(
  val user: KailleraUser,
  val server: KailleraServer,
  val clientHandler: V086ClientHandler,
  val registry: CommandRegistry,
  /** The context in which this command was dispatched. */
  val currentContext: CommandContext,
  /**
   * Non-null when [currentContext] is [CommandContext.GAME_CHAT] or [CommandContext.GAME_OWNER].
   */
  val game: KailleraGame? = null,
) {
  /** Send an informational message visible only to the invoking user (server lobby). */
  fun sendInfo(text: String) {
    try {
      clientHandler.send(
        org.emulinker.kaillera.controller.v086.protocol.InformationMessage(0, "server", text)
      )
    } catch (_: Exception) {}
  }

  /** Announce a message to the game room (or privately if [privateUser] is set). */
  fun announceGame(text: String, privateUser: KailleraUser? = null) {
    game?.announce(text, privateUser)
  }
}

/**
 * A single server slash command.
 *
 * Implement this interface for every `/command` the server supports. Register instances with
 * [CommandRegistry] via Koin injection so they are automatically included in dispatch and `/help`.
 */
interface ServerCommand {
  /** The primary token the command starts with, including the slash (e.g. `"/ban"`). */
  val name: String

  /**
   * Usage string shown in `/help`, e.g. `"/ban <UserID> <minutes> [reason]"`. Use `[arg]` for
   * optional arguments.
   */
  val usage: String

  /** One-line human-readable description for `/help` output. */
  val description: String

  /**
   * Minimum [AccessManager] access level required to invoke this command.
   *
   * Defaults to [AccessManager.ACCESS_NORMAL] (all logged-in users).
   */
  val minimumAccessLevel: Int
    get() = AccessManager.ACCESS_NORMAL

  /**
   * The set of contexts in which this command may be invoked. Commands registered for multiple
   * contexts share a single implementation, avoiding duplication.
   */
  val contexts: Set<CommandContext>

  /**
   * Returns `true` if this command handles the given raw message string.
   *
   * The default implementation matches any string that starts with [name] followed by end-of-input
   * or a space, which works for the vast majority of commands. Override for commands with special
   * matching rules (e.g. exact-match variants like `/start` vs `/startn`).
   */
  fun matches(rawMessage: String): Boolean = rawMessage == name || rawMessage.startsWith("$name ")

  /**
   * Execute this command.
   *
   * @param args The full raw message string as typed by the user (e.g. `"/ban 5 10 griefing"`).
   * @param ctx Contextual state for this invocation.
   */
  fun execute(args: String, ctx: CommandExecutionContext)
}
