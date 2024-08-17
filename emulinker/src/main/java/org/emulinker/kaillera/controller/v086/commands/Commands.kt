package org.emulinker.kaillera.controller.v086.commands

import com.google.common.flogger.FluentLogger
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.util.EmuUtil.threadSleep

private val logger = FluentLogger.forEnclosingClass()

sealed interface ParseResult {
  data object Success : ParseResult

  @JvmInline value class Failure(val messageToUser: String) : ParseResult
}

enum class CommandUsageLocation {
  ANYWHERE,
  ROOM_CHAT,
  SERVER_CHAT,
}

fun provideGameChatCommands(twitterBroadcaster: TwitterBroadcaster): List<GameChatCommand> =
  listOf(
    object :
      GameChatCommand(
        prefix = "msgon",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        clientHandler.user.isAcceptingDirectMessages = true
        game.announce("Private messages are now on.", clientHandler.user)
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "msgoff",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        clientHandler.user.isAcceptingDirectMessages = false
        game.announce("Private messages are now off.", clientHandler.user)
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "p2pon",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        if (game.owner == clientHandler.user) {
          game.ignoringUnnecessaryServerActivity = true
          for (u in game.players) {
            u.ignoringUnnecessaryServerActivity = true
            if (u.loggedIn) {
              game.announce("This game will NOT receive any server activity during gameplay!", u)
            }
          }
        } else {
          clientHandler.user.ignoringUnnecessaryServerActivity = true
          for (u in game.players) {
            if (u.loggedIn) {
              game.announce(
                "${clientHandler.user.name} will NOT receive any server activity during gameplay!",
                u
              )
            }
          }
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "p2poff",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        if (game.owner == clientHandler.user) {
          game.ignoringUnnecessaryServerActivity = false
          for (u in game.players) {
            u.ignoringUnnecessaryServerActivity = false
            if (u.loggedIn) {
              game.announce("This game will NOW receive ALL server activity during gameplay!", u)
            }
          }
        } else {
          clientHandler.user.ignoringUnnecessaryServerActivity = false
          for (u in game.players) {
            if (u.loggedIn) {
              game.announce(
                clientHandler.user.name + " will NOW receive ALL server activity during gameplay!",
                u
              )
            }
          }
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "msg",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        val scanner = java.util.Scanner(message).useDelimiter(" ")
        val access =
          clientHandler.user.server.accessManager.getAccess(
            clientHandler.user.socketAddress!!.address
          )
        if (
          access < AccessManager.ACCESS_SUPERADMIN &&
            clientHandler.user.server.accessManager.isSilenced(
              clientHandler.user.socketAddress!!.address
            )
        ) {
          game.announce("You are silenced!", clientHandler.user)
          return
        }
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          val sb = StringBuilder()
          while (scanner.hasNext()) {
            sb.append(scanner.next())
            sb.append(" ")
          }
          if (user == null) {
            game.announce("User not found!", clientHandler.user)
            return
          }
          if (user.game != game) {
            game.announce("User not in this game!", clientHandler.user)
            return
          }
          if (user === clientHandler.user) {
            game.announce("You can't private message yourself!", clientHandler.user)
            return
          }
          if (
            !user.isAcceptingDirectMessages ||
              user.searchIgnoredUsers(clientHandler.user.connectSocketAddress.address.hostAddress)
          ) {
            game.announce(
              "<" + user.name + "> Is not accepting private messages!",
              clientHandler.user
            )
            return
          }
          var m = sb.toString()
          m = m.trim { it <= ' ' }
          if (m.isBlank() || m.startsWith("�") || m.startsWith("�")) return
          if (access == AccessManager.ACCESS_NORMAL) {
            val chars = m.toCharArray()
            for (i in chars.indices) {
              if (chars[i].code < 32) {
                logger.atWarning().log("%s /msg denied: Illegal characters in message", user)
                game.announce(
                  "Private Message Denied: Illegal characters in message",
                  clientHandler.user
                )
                return
              }
            }
            if (m.length > 320) {
              logger.atWarning().log("%s /msg denied: Message Length > 320", user)
              game.announce("Private Message Denied: Message Too Long", clientHandler.user)
              return
            }
          }
          clientHandler.user.lastMsgID = user.id
          user.lastMsgID = clientHandler.user.id

          // user1.getServer().announce("TO: <" + user.getName() + ">(" + user.getID() + ") <" +
          // clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): "
          // +
          // m, false, user1);
          // user.getServer().announce("<" + clientHandler.getUser().getName() + "> (" +
          // clientHandler.getUser().getID() + "): " + m, false, user);
          game.announce(
            "TO: <${user.name}>(${user.id}) <${clientHandler.user.name}> (${clientHandler.user.id}): $m",
            clientHandler.user
          )
          user.game?.announce("<${clientHandler.user.name}> (${clientHandler.user.id}): $m", user)
          return
        } catch (e: java.util.NoSuchElementException) {
          if (clientHandler.user.lastMsgID != -1) {
            try {
              val user = clientHandler.user.server.getUser(clientHandler.user.lastMsgID)
              val sb = StringBuilder()
              while (scanner.hasNext()) {
                sb.append(scanner.next())
                sb.append(" ")
              }
              if (user == null) {
                game.announce("User not found!", clientHandler.user)
                return
              }
              if (user.game != game) {
                game.announce("User not in this game!", clientHandler.user)
                return
              }
              if (user === clientHandler.user) {
                game.announce("You can't private message yourself!", clientHandler.user)
                return
              }
              var m = sb.toString()
              m = m.trim { it <= ' ' }
              if (m.isBlank() || m.startsWith("�") || m.startsWith("�")) return
              if (access == AccessManager.ACCESS_NORMAL) {
                val chars = m.toCharArray()
                var i = 0
                while (i < chars.size) {
                  if (chars[i].code < 32) {
                    logger.atWarning().log("%s /msg denied: Illegal characters in message", user)
                    game.announce(
                      "Private Message Denied: Illegal characters in message",
                      clientHandler.user
                    )
                    return
                  }
                  i++
                }
                if (m.length > 320) {
                  logger.atWarning().log("%s /msg denied: Message Length > 320", user)
                  game.announce("Private Message Denied: Message Too Long", clientHandler.user)
                  return
                }
              }

              // user1.getServer().announce("TO: <" + user.getName() + ">(" + user.getID() + ")
              // <"
              // +
              // clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() +
              // "):
              // "
              // + m, false, user1);
              // user.getServer().announce("<" + clientHandler.getUser().getName() + "> (" +
              // clientHandler.getUser().getID() + "): " + m, false, user);
              game.announce(
                "TO: <${user.name}>(${user.id}) <${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                clientHandler.user
              )
              user.game?.announce(
                "<${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                user
              )
              return
            } catch (e1: Exception) {
              game.announce("Private Message Error: /msg <UserID> <message>", clientHandler.user)
              return
            }
          } else {
            game.announce("Private Message Error: /msg <UserID> <message>", clientHandler.user)
            return
          }
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "ignoreall",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        clientHandler.user.ignoreAll = true
        clientHandler.user.server.announce(
          clientHandler.user.name + " is now ignoring everyone!",
          false,
          null
        )
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "unignoreall",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        clientHandler.user.ignoreAll = false
        clientHandler.user.server.announce(
          clientHandler.user.name + " is now unignoring everyone!",
          gamesAlso = false,
          targetUser = null
        )
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "ignore",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        val scanner = java.util.Scanner(message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            game.announce("User not found!", clientHandler.user)
            return
          }
          if (user === clientHandler.user) {
            game.announce("You can't ignore yourself!", clientHandler.user)
            return
          }
          if (clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            game.announce("You can't ignore a user that is already ignored!", clientHandler.user)
            return
          }
          if (user.accessLevel >= AccessManager.ACCESS_MODERATOR) {
            game.announce("You cannot ignore a moderator or admin!", clientHandler.user)
            return
          }
          clientHandler.user.addIgnoredUser(user.connectSocketAddress.address.hostAddress)
          user.server.announce(
            "${clientHandler.user.name} is now ignoring <${user.name}> ID: ${user.id}",
            false,
            null
          )
          return
        } catch (e: java.util.NoSuchElementException) {
          game.announce("Ignore User Error: /ignore <UserID>", clientHandler.user)
          logger
            .atInfo()
            .withCause(e)
            .log(
              "IGNORE USER ERROR: %s: %s",
              clientHandler.user.name,
              clientHandler.remoteSocketAddress!!.hostName
            )
          return
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "unignore",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        val scanner = java.util.Scanner(message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            game.announce("User Not Found!", clientHandler.user)
            return
          }
          if (!clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            game.announce("You can't unignore a user that isn't ignored", clientHandler.user)
            return
          }
          if (
            clientHandler.user.removeIgnoredUser(
              user.connectSocketAddress.address.hostAddress,
              false
            )
          )
            user.server.announce(
              "${clientHandler.user.name} is now unignoring <${user.name}> ID: ${user.id}",
              false,
              null
            )
          else
            clientHandler.send(
              org.emulinker.kaillera.controller.v086.protocol.InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "User Not Found!"
              )
            )
        } catch (e: java.util.NoSuchElementException) {
          game.announce("Unignore User Error: /ignore <UserID>", clientHandler.user)
          logger
            .atInfo()
            .withCause(e)
            .log(
              "UNIGNORE USER ERROR: %s: %s",
              clientHandler.user.name,
              clientHandler.remoteSocketAddress!!.hostName
            )
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "me",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        val space = message.indexOf(' ')
        if (space < 0) {
          game.announce("Invalid # of Fields!", clientHandler.user)
          return
        }
        var announcement = message.substring(space + 1)
        if (announcement.startsWith(":"))
          announcement =
            announcement.substring(
              1
            ) // this protects against people screwing up the emulinker supraclient
        val access =
          clientHandler.user.server.accessManager.getAccess(
            clientHandler.user.socketAddress!!.address
          )
        if (
          access < AccessManager.ACCESS_SUPERADMIN &&
            clientHandler.user.server.accessManager.isSilenced(
              clientHandler.user.socketAddress!!.address
            )
        ) {
          game.announce("You are silenced!", clientHandler.user)
          return
        }
        if (clientHandler.user.server.checkMe(clientHandler.user, announcement)) {
          val m = announcement
          announcement = "*" + clientHandler.user.name + " " + m
          for (user in game.players) {
            game.announce(announcement, user)
          }
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        game.announce(
          "/me <message> to make personal message eg. /me is bored ...SupraFast is bored.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        game.announce(
          "/msg <UserID> <msg> to PM somebody. /msgoff or /msgon to turn pm off | on.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        game.announce(
          "/ignore <UserID> or /unignore <UserID> or /ignoreall or /unignoreall to ignore users.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        game.announce(
          "/p2pon or /p2poff this option ignores all server activity during gameplay.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    object :
      GameChatCommand(
        prefix = "stop",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        if (twitterBroadcaster.cancelActionsForUser(clientHandler.user.id)) {
          game.announce(
            org.emulinker.util.EmuLang.getStringOrDefault(
              "KailleraServerImpl.CanceledPendingTweet",
              default = "Canceled pending tweet."
            ),
            clientHandler.user
          )
        } else {
          game.announce("No pending tweets.", clientHandler.user)
        }
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    },
    // TODO(nue): Handle the "else" case.
    object :
      GameChatCommand(
        prefix = "UNKNOWN",
        commandPermissions = CommandPermissions.LENIENT,
      ) {
      override fun performAction(
        message: String,
        game: KailleraGameImpl,
        clientHandler: V086ClientHandler
      ) {
        TODO("Not yet implemented")
      }

      override fun matches(command: String): ParseResult {
        TODO("Not yet implemented")
      }
    }
  )

class CommandPermissions(
  val allowedLocations: CommandUsageLocation = CommandUsageLocation.ANYWHERE,
  val gameOwnerOnly: Boolean = false,
  val minimumAccessRequired: Int = AccessManager.ACCESS_NORMAL,
) {
  companion object {
    val LENIENT =
      CommandPermissions(
        allowedLocations = CommandUsageLocation.ANYWHERE,
        gameOwnerOnly = false,
        minimumAccessRequired = AccessManager.ACCESS_NORMAL
      )
  }
}

abstract class GameChatCommand(
  /** For the command `/maxusers 42`, this would be `"maxusers"`. */
  val prefix: String,
  val commandPermissions: CommandPermissions,
) {
  protected abstract fun performAction(
    message: String,
    game: KailleraGameImpl,
    clientHandler: V086ClientHandler
  )

  /** Validates whether the command is valid. */
  protected abstract fun matches(command: String): ParseResult

  fun handle(message: String, game: KailleraGameImpl, handler: V086ClientHandler) {
    when (val parseResult = matches(message.trim())) {
      ParseResult.Success -> {
        performAction(message, game, handler)
      }
      is ParseResult.Failure -> {
        parseResult.messageToUser
        // TODO(nue): Send messageToUser to user.
      }
    }
  }

  companion object {
    const val COMMAND_PREFIX = "/"
  }
}
