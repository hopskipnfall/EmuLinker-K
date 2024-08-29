package org.emulinker.kaillera.controller.v086.commands

import com.google.common.flogger.FluentLogger
import java.util.NoSuchElementException
import java.util.Scanner
import java.util.StringTokenizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.threadSleep

private val logger = FluentLogger.forEnclosingClass()

sealed interface ParseResult {
  data object Success : ParseResult

  @JvmInline value class Failure(val messageToUser: String) : ParseResult
}

enum class CommandUsageLocation {
  ANYWHERE,
  GAME_CHAT_ONLY,
  SERVER_CHAT_ONLY,
}

class CommandPermissions(
  val allowedLocations: CommandUsageLocation,
  val gameOwnerOnly: Boolean,
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

abstract class ChatCommand() {
  /** For the command `/maxusers 42`, this would be `"maxusers"`. */
  abstract val prefix: String
  abstract val commandPermissions: CommandPermissions


}

abstract class GameChatCommand(
  /** For the command `/maxusers 42`, this would be `"maxusers"`. */
   override val prefix: String,
   override val commandPermissions: CommandPermissions,
): ChatCommand() {
  protected abstract fun performAction(
    args: String,
    game: KailleraGameImpl,
    clientHandler: V086ClientHandler
  )

  /** Validates whether the command is valid. */
  protected abstract fun verifyArgs(args: String): ParseResult

  /** @param message Does not start with "/". */
  fun handle(message: String, game: KailleraGameImpl, handler: V086ClientHandler) {
    if (commandPermissions.allowedLocations == CommandUsageLocation.SERVER_CHAT_ONLY) {
      TODO("Can only do this in server chat!")
    }

    if (commandPermissions.gameOwnerOnly && game.owner.id != handler.user.id) {
      TODO("Not the owner!")
    }

    if (commandPermissions.minimumAccessRequired > handler.user.accessLevel) {
      TODO("Insufficient access level!")
    }

    val args = message.removePrefix(COMMAND_PREFIX + prefix).trim()
    when (val parseResult = verifyArgs(args)) {
      ParseResult.Success -> {
        performAction(args, game, handler)
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

@Singleton
class GameChatCommandHandler @Inject constructor(twitterBroadcaster: TwitterBroadcaster) {

  val commands =
    listOf<GameChatCommand>(
      object :
        GameChatCommand(
          prefix = "msgon",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          clientHandler.user.isAcceptingDirectMessages = true
          game.announce("Private messages are now on.", clientHandler.user)
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "msgoff",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          clientHandler.user.isAcceptingDirectMessages = false
          game.announce("Private messages are now off.", clientHandler.user)
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "p2pon",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
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

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "p2poff",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
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
                  clientHandler.user.name +
                    " will NOW receive ALL server activity during gameplay!",
                  u
                )
              }
            }
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "msg",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
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
          } catch (e: NoSuchElementException) {
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

        override fun verifyArgs(args: String): ParseResult =
          if (args.isNotBlank()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "ignoreall",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
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

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "unignoreall",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
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

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "ignore",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
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
          } catch (e: NoSuchElementException) {
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

        override fun verifyArgs(args: String): ParseResult {
          TODO("Not yet implemented")
        }
      },
      object :
        GameChatCommand(
          prefix = "unignore",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            scanner.next()
            val userID = scanner.nextInt()
            val user = clientHandler.user.server.getUser(userID)
            if (user == null) {
              game.announce("User Not Found!", clientHandler.user)
              return
            }
            if (
              !clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)
            ) {
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
          } catch (e: NoSuchElementException) {
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

        override fun verifyArgs(args: String): ParseResult {
          TODO("Not yet implemented")
        }
      },
      object :
        GameChatCommand(
          prefix = "me",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          var announcement = args
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

        override fun verifyArgs(args: String): ParseResult =
          if (args.isNotBlank()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "help",
          commandPermissions = CommandPermissions.LENIENT,
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          if (game.owner.id == clientHandler.user.id) {
            val admin = clientHandler.user
            if (admin != game.owner && admin.accessLevel < AccessManager.ACCESS_SUPERADMIN) return
            // game.setIndividualGameAnnounce(admin.getPlayerNumber());
            // game.announce(EmuLang.getString("GameOwnerCommandAction.AvailableCommands"));
            // try { Thread.sleep(20); } catch(Exception e) {}
            game.announce(EmuLang.getString("GameOwnerCommandAction.SetAutofireDetection"), admin)
            threadSleep(20.milliseconds)
            game.announce("/maxusers <#> to set capacity of room", admin)
            threadSleep(20.milliseconds)
            game.announce("/maxping <#> to set maximum ping for room", admin)
            threadSleep(20.milliseconds)
            game.announce("/start or /startn <#> start game when n players are joined.", admin)
            threadSleep(20.milliseconds)
            game.announce(
              "/mute /unmute  <UserID> or /muteall or /unmuteall to mute player(s).",
              admin
            )
            threadSleep(20.milliseconds)
            game.announce(
              "/swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
              admin
            )
            threadSleep(20.milliseconds)
            game.announce("/kick <Player#> or /kickall to kick a player(s).", admin)
            threadSleep(20.milliseconds)
            game.announce("/setemu To restrict the gameroom to this emulator!", admin)
            threadSleep(20.milliseconds)
            game.announce("/setconn To restrict the gameroom to this connection type!", admin)
            threadSleep(20.milliseconds)
            game.announce(
              "/lagstat To check who has the most lag spikes or /lagreset to reset lagstat!",
              admin
            )
            threadSleep(20.milliseconds)
            game.announce(
              "/samedelay {true | false} to play at the same delay as player with highest ping. Default is false.",
              admin
            )
            threadSleep(20.milliseconds)
          } else {
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
        }

        override fun verifyArgs(args: String): ParseResult = ParseResult.Success
      },
      object :
        GameChatCommand(
          prefix = "stop",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          if (twitterBroadcaster.cancelActionsForUser(clientHandler.user.id)) {
            game.announce(
              EmuLang.getStringOrDefault(
                "KailleraServerImpl.CanceledPendingTweet",
                default = "Canceled pending tweet."
              ),
              clientHandler.user
            )
          } else {
            game.announce("No pending tweets.", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "detectautofire",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          fun autoFireHelp(game: KailleraGameImpl, admin: KailleraUser) {
            val cur = game.autoFireDetector!!.sensitivity
            game.announce(EmuLang.getString("GameOwnerCommandAction.HelpSensitivity"), admin)
            threadSleep(20.milliseconds)
            game.announce(EmuLang.getString("GameOwnerCommandAction.HelpDisable"), admin)
            threadSleep(20.milliseconds)
            game.announce(
              EmuLang.getString("GameOwnerCommandAction.HelpCurrentSensitivity", cur) +
                if (cur == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled") else "",
              admin
            )
          }

          val admin = clientHandler.user
          if (game.status != GameStatus.WAITING) {
            game.announce(
              EmuLang.getString("GameOwnerCommandAction.AutoFireChangeDeniedInGame"),
              admin
            )
            return
          }
          val st = StringTokenizer(args, " ")
          if (st.countTokens() != 2) {
            autoFireHelp(game, admin)
            return
          }
          val unusedCommand = st.nextToken()
          val sensitivityStr = st.nextToken()
          var sensitivity = -1
          try {
            sensitivity = sensitivityStr.toInt()
          } catch (e: NumberFormatException) {}
          if (sensitivity > 5 || sensitivity < 0) {
            autoFireHelp(game, admin)
            return
          }
          game.autoFireDetector!!.sensitivity = sensitivity
          game.announce(
            EmuLang.getString("GameOwnerCommandAction.HelpCurrentSensitivity", sensitivity) +
              if (sensitivity == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled")
              else "",
          )
        }

        override fun verifyArgs(args: String): ParseResult = TODO()
      },
      object :
        GameChatCommand(
          prefix = "maxusers",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val admin = clientHandler.user
          if (System.currentTimeMillis() - lastMaxUserChange <= 3000) {
            game.announce("Max User Command Spam Detection...Please Wait!", admin)
            lastMaxUserChange = System.currentTimeMillis()
            return
          } else {
            lastMaxUserChange = System.currentTimeMillis()
          }
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val num = scanner.nextInt()
            if (num in 1..100) {
              game.maxUsers = num
              game.announce(
                "Max Users has been set to $num",
              )
            } else {
              game.announce("Max Users Error: Enter value between 1 and 100", admin)
            }
          } catch (e: NoSuchElementException) {
            game.announce("Failed: /maxusers <#>", admin)
          }
        }

        override fun verifyArgs(args: String): ParseResult = TODO()
      },
      object :
        GameChatCommand(
          prefix = "maxping",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val num = scanner.nextInt()
            if (num in 1..1000) {
              game.maxPing = num
              game.announce(
                "Max Ping has been set to $num",
              )
            } else {
              game.announce("Max Ping Error: Enter value between 1 and 1000", clientHandler.user)
            }
          } catch (e: NoSuchElementException) {
            game.announce("Failed: /maxping <#>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "start",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          game.start(clientHandler.user)
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "startn",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val num = scanner.nextInt()
            if (num in 1..100) {
              game.startN = num.toByte().toInt()
              game.announce(
                "This game will start when $num players have joined.",
              )
            } else {
              game.announce("StartN Error: Enter value between 1 and 100.", clientHandler.user)
            }
          } catch (e: NoSuchElementException) {
            game.announce("Failed: /startn <#>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "mute",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val userID = scanner.nextInt()
            val user = clientHandler.user.server.getUser(userID)
            if (user == null) {
              game.announce("Player doesn't exist!", clientHandler.user)
              return
            }
            if (user === clientHandler.user) {
              game.announce("You can't mute yourself!", clientHandler.user)
              return
            }
            if (
              user.accessLevel >= AccessManager.ACCESS_ADMIN &&
                clientHandler.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
            ) {
              user.game!!.announce("You can't mute an Admin", clientHandler.user)
              return
            }

            // mute by IP
            game.mutedUsers.add(user.connectSocketAddress.address.hostAddress)
            user.isMuted = true
            val user1 = clientHandler.user
            user1.game!!.announce(
              user.name + " has been muted!",
            )
          } catch (e: NoSuchElementException) {
            val user = clientHandler.user
            user.game!!.announce("Mute Player Error: /mute <UserID>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "muteall",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            for (w in 1..game.players.size) {
              // do not mute owner or admin
              if (
                game.getPlayer(w)!!.accessLevel < AccessManager.ACCESS_ADMIN &&
                  game.getPlayer(w) != game.owner
              ) {
                game.getPlayer(w)!!.isMuted = true
                game.mutedUsers.add(game.getPlayer(w)!!.connectSocketAddress.address.hostAddress)
              }
            }
            game.announce(
              "All players have been muted!",
            )
            return
          } catch (e: NoSuchElementException) {
            game.announce("Mute Player Error: /mute <UserID>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "setemu",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          var emu = game.owner.clientType
          if (args == "any") {
            emu = "any"
          }
          game.aEmulator = emu!!
          game.announce(
            "Owner has restricted the emulator to: $emu",
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "setconn",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = true,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          var conn = game.owner.connectionType.readableName
          if (args == "any") {
            conn = "any"
          }
          game.aConnection = conn
          game.announce(
            "Owner has restricted the connection type to: $conn",
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "unmute",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val userID = scanner.nextInt()
            val user = clientHandler.user.server.getUser(userID)
            if (user == null) {
              game.announce("Player doesn't exist!", clientHandler.user)
              return
            }
            if (user === clientHandler.user) {
              user.game!!.announce("You can't unmute yourself!", clientHandler.user)
              return
            }
            if (
              user.accessLevel >= AccessManager.ACCESS_ADMIN &&
                clientHandler.user.accessLevel != AccessManager.ACCESS_SUPERADMIN
            ) {
              user.game!!.announce("You can't unmute an Admin", clientHandler.user)
              return
            }
            game.mutedUsers.remove(user.connectSocketAddress.address.hostAddress)
            user.isMuted = false
            val user1 = clientHandler.user
            user1.game!!.announce(
              user.name + " has been unmuted!",
            )
          } catch (e: NoSuchElementException) {
            val user = clientHandler.user
            user.game!!.announce("Unmute Player Error: /unmute <UserID>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "unmuteall",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          try {
            game.players.forEach { kailleraUser ->
              kailleraUser.isMuted = false
              game.mutedUsers.remove(kailleraUser.connectSocketAddress.address.hostAddress)
            }
            game.announce(
              "All players have been unmuted!",
            )
          } catch (e: NoSuchElementException) {
            val user = clientHandler.user
            user.game!!.announce("Unmute Player Error: /unmute <UserID>", clientHandler.user)
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "swap",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          /*if(game.getStatus() != KailleraGame.STATUS_PLAYING){
              game.announce("Failed: wap Players can only be used during gameplay!", admin);
              return;
          }*/
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            var i: Int
            val str: String
            val test = scanner.nextInt()
            str = test.toString()
            if (game.players.size < str.length) {
              game.announce(
                "Failed: You can't swap more than the # of players in the room.",
                clientHandler.user
              )
              return
            }
            if (test > 0) {
              var numCount = 0
              val num = IntArray(str.length)
              // before swap check numbers to prevent errors due to incorrectly entered numbers
              i = 0
              while (i < num.size) {
                num[i] = str[i].toString().toInt()
                numCount = 1
                if (num[i] == 0 || num[i] > game.players.size) break
                for (j in num.indices) {
                  if (num[i] != num[j]) numCount++
                }
                i++
              }
              if (numCount == game.players.size) {
                game.swap = true
                // PlayerActionQueue temp = game.getPlayerActionQueue()[0];
                i = 0
                while (i < str.length) {
                  val player = game.players[i]
                  player.playerNumber = num[i]
                  /*if(num[i] == 1){
                      game.getPlayerActionQueue()[i] = temp;
                  }
                  else{
                      game.getPlayerActionQueue()[i] = game.getPlayerActionQueue()[num[i]-1];
                  }*/ game.announce(
                    player.name + " is now Player#: " + player.playerNumber,
                  )
                  i++
                }
              } else
                game.announce(
                  "Swap Player Error: /swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
                  clientHandler.user
                )
            }
          } catch (e: NoSuchElementException) {
            game.announce(
              "Swap Player Error: /swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
              clientHandler.user
            )
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "kick",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val scanner = Scanner(args).useDelimiter(" ")
          try {
            val playerNumber = scanner.nextInt()
            if (playerNumber in 1..100) {
              if (game.getPlayer(playerNumber) != null)
                game.kick(clientHandler.user, game.getPlayer(playerNumber)!!.id)
              else {
                game.announce("Player doesn't exisit!", clientHandler.user)
              }
            } else {
              game.announce("Kick Player Error: Enter value between 1 and 100", clientHandler.user)
            }
          } catch (e: NoSuchElementException) {
            game.announce(
              "Failed: /kick <Player#> or /kickall to kick all players.",
              clientHandler.user
            )
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "kickall",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          // start kick players from last to first and don't kick owner or admin
          for (w in game.players.size downTo 1) {
            if (
              game.getPlayer(w)!!.accessLevel < AccessManager.ACCESS_ADMIN &&
                game.getPlayer(w) != game.owner
            )
              game.kick(clientHandler.user, game.getPlayer(w)!!.id)
          }
          game.announce(
            "All players have been kicked!",
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "lagstat",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          game.announce("Lagged frames per player:")
          game.players
            .asSequence()
            .filter { !it.inStealthMode }
            .forEach {
              game.announce(
                "P${it.playerNumber}: ${it.smallLagSpikesCausedByUser} (tiny), ${it.bigLagSpikesCausedByUser} (big)"
              )
            }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "lagreset",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          for (player in game.players) {
            player.timeouts = 0
            player.smallLagSpikesCausedByUser = 0
            player.bigLagSpikesCausedByUser = 0
          }
          game.announce(
            "LagStat has been reset!",
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "samedelay",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          if (args == "true") {
            game.sameDelay = true
            game.announce(
              "Players will have the same delay when game starts (restarts)!",
            )
          } else {
            game.sameDelay = false
            game.announce(
              "Players will have independent delays when game starts (restarts)!",
            )
          }
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "num",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.GAME_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          game.announce("${game.players.size} in the room!", clientHandler.user)
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      // START OF SERVER CHAT COMMANDS
      object :
        GameChatCommand(
          prefix = "alivecheck",
          commandPermissions =
            CommandPermissions(
              allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
              gameOwnerOnly = false,
            ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              ":ALIVECHECK=EmuLinker-K Alive Check: You are still logged in."
            )
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "version",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          val releaseInfo = clientHandler.user.server.releaseInfo
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "VERSION: ${releaseInfo.productName}: ${releaseInfo.version}: ${EmuUtil.toSimpleUtcDatetime(releaseInfo.buildDate)}"
              )
            )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "myip",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "Your IP Address is: " + clientHandler.user.connectSocketAddress.address.hostAddress
            )
          )
        }

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
      object :
        GameChatCommand(
          prefix = "TEMPLATE",
          commandPermissions =
          CommandPermissions(
            allowedLocations = CommandUsageLocation.SERVER_CHAT_ONLY,
            gameOwnerOnly = false,
          ),
        ) {
        override fun performAction(
          args: String,
          game: KailleraGameImpl,
          clientHandler: V086ClientHandler
        ) {}

        override fun verifyArgs(args: String): ParseResult =
          if (args.isEmpty()) ParseResult.Success else TODO("Write a message")
      },
    )

  /**
   * Handler for the `/help` command.
   *
   * Lists commands available to the user where they are using the command.
   */
  fun help(game: KailleraGameImpl?, clientHandler: V086ClientHandler) {
    for (it in commands) {
      if (
        (it.commandPermissions.allowedLocations == CommandUsageLocation.SERVER_CHAT_ONLY &&
          game != null) ||
          (it.commandPermissions.allowedLocations == CommandUsageLocation.GAME_CHAT_ONLY &&
            game == null)
      ) {
        continue
      }

      if (
        clientHandler.user.accessLevel >= AccessManager.ACCESS_SUPERADMIN ||
          clientHandler.user.accessLevel >= it.commandPermissions.minimumAccessRequired
      ) {
        if (game == null) {
          TODO("Support server chat")
        } else {
          game.announce(it.prefix, clientHandler.user)
        }
      }
    }
  }

  companion object {
    private var lastMaxUserChange: Long = 0
  }
}
