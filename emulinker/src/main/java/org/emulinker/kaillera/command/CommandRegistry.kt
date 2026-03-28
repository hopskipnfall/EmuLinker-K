package org.emulinker.kaillera.command

import com.google.common.flogger.FluentLogger

/**
 * Registry of all [ServerCommand] instances in the server.
 *
 * Injected with all registered commands via Koin. Handles dispatch and help-text generation.
 */
class CommandRegistry(private val commands: List<ServerCommand>) {

  /** Returns all commands valid in [context], sorted by name. */
  fun forContext(context: CommandContext): List<ServerCommand> =
    commands.filter { context in it.contexts }.sortedBy { it.name }

  /**
   * Finds the first command that [matches][ServerCommand.matches] [rawMessage] and is valid in
   * [context].
   */
  fun find(rawMessage: String, context: CommandContext): ServerCommand? =
    commands.firstOrNull { context in it.contexts && it.matches(rawMessage) }

  /**
   * Generates help-text lines for a user with [userAccessLevel] in [context].
   *
   * Only commands the user has sufficient access to see are included. Each line is formatted as:
   * `"<usage> — <description>"`.
   */
  fun helpLines(context: CommandContext, userAccessLevel: Int): List<String> =
    forContext(context)
      .filter { userAccessLevel >= it.minimumAccessLevel }
      .map { "${it.usage}  —  ${it.description}" }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
