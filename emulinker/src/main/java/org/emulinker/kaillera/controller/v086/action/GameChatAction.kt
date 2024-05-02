package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.*
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil.threadSleep

@Singleton
class GameChatAction
@Inject
internal constructor(
  private val gameOwnerCommandAction: GameOwnerCommandAction,
  private val lookingForGameReporter: TwitterBroadcaster
) : V086Action<GameChatRequest>, V086GameEventHandler<GameChatEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "GameChatAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: GameChatRequest, clientHandler: V086ClientHandler) {
    if (clientHandler.user.game == null) return
    if (message.message.startsWith(ADMIN_COMMAND_ESCAPE_STRING)) {
      // if(clientHandler.getUser().getAccess() >= AccessManager.ACCESS_ADMIN ||
      // clientHandler.getUser().equals(clientHandler.getUser().getGame().getOwner())){
      try {
        if (gameOwnerCommandAction.isValidCommand((message as GameChat).message)) {
          gameOwnerCommandAction.performAction(message, clientHandler)
          if ((message as GameChat).message == "/help") checkCommands(message, clientHandler)
        } else checkCommands(message, clientHandler)
        return
      } catch (e: FatalActionException) {
        logger.atWarning().withCause(e).log("GameOwner command failed")
      }

      // }
    }
    actionPerformedCount++
    try {
      clientHandler.user.gameChat(message.message, message.messageNumber)
    } catch (e: GameChatException) {
      logger.atSevere().withCause(e).log("Failed to send game chat message")
    }
  }

  @Throws(FatalActionException::class)
  private fun checkCommands(message: V086Message, clientHandler: V086ClientHandler?) {
    var doCommand = true
    if (clientHandler!!.user.accessLevel < AccessManager.ACCESS_ELEVATED) {
      try {
        clientHandler.user.chat(":USER_COMMAND")
      } catch (e: ActionException) {
        doCommand = false
      }
    }
    if (doCommand) {
      if ((message as GameChat).message == "/msgon") {
        try {
          clientHandler.user.isAcceptingDirectMessages = true
          clientHandler.user.game!!.announce("Private messages are now on.", clientHandler.user)
        } catch (e: Exception) {}
        return
      } else if (message.message == "/msgoff") {
        try {
          clientHandler.user.isAcceptingDirectMessages = false
          clientHandler.user.game!!.announce("Private messages are now off.", clientHandler.user)
        } catch (e: Exception) {}
        return
      } else if (message.message.startsWith("/p2p")) {
        if (message.message == "/p2pon") {
          if (clientHandler.user.game!!.owner == clientHandler.user) {
            clientHandler.user.game!!.ignoringUnnecessaryServerActivity = true
            for (u in clientHandler.user.game!!.players) {
              u.ignoringUnnecessaryServerActivity = true
              if (u.loggedIn) {
                u.game!!.announce(
                  "This game will NOT receive any server activity during gameplay!",
                  u
                )
              }
            }
          } else {
            clientHandler.user.ignoringUnnecessaryServerActivity = true
            for (u in clientHandler.user.game!!.players) {
              if (u.loggedIn) {
                u.game!!.announce(
                  "${clientHandler.user.name} will NOT receive any server activity during gameplay!",
                  u
                )
              }
            }
          }
        } else if (message.message == "/p2poff") {
          if (clientHandler.user.game!!.owner == clientHandler.user) {
            clientHandler.user.game!!.ignoringUnnecessaryServerActivity = false
            for (u in clientHandler.user.game!!.players) {
              u.ignoringUnnecessaryServerActivity = false
              if (u.loggedIn) {
                u.game!!.announce(
                  "This game will NOW receive ALL server activity during gameplay!",
                  u
                )
              }
            }
          } else {
            clientHandler.user.ignoringUnnecessaryServerActivity = false
            for (u in clientHandler.user.game!!.players) {
              if (u.loggedIn) {
                u.game!!.announce(
                  clientHandler.user.name +
                    " will NOW receive ALL server activity during gameplay!",
                  u
                )
              }
            }
          }
        } else {
          clientHandler.user.game!!.announce("Failed P2P: /p2pon or /p2poff", clientHandler.user)
        }
        return
      } else if (message.message.startsWith("/msg")) {
        val scanner = Scanner(message.message).useDelimiter(" ")
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
          clientHandler.user.game!!.announce("You are silenced!", clientHandler.user)
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
            clientHandler.user.game!!.announce("User not found!", clientHandler.user)
            return
          }
          if (user.game != clientHandler.user.game) {
            clientHandler.user.game!!.announce("User not in this game!", clientHandler.user)
            return
          }
          if (user === clientHandler.user) {
            clientHandler.user.game!!.announce(
              "You can't private message yourself!",
              clientHandler.user
            )
            return
          }
          if (
            !user.isAcceptingDirectMessages ||
              user.searchIgnoredUsers(clientHandler.user.connectSocketAddress.address.hostAddress)
          ) {
            clientHandler.user.game!!.announce(
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
                clientHandler.user.game!!.announce(
                  "Private Message Denied: Illegal characters in message",
                  clientHandler.user
                )
                return
              }
            }
            if (m.length > 320) {
              logger.atWarning().log("%s /msg denied: Message Length > 320", user)
              clientHandler.user.game!!.announce(
                "Private Message Denied: Message Too Long",
                clientHandler.user
              )
              return
            }
          }
          clientHandler.user.lastMsgID = user.id
          user.lastMsgID = clientHandler.user.id

          // user1.getServer().announce("TO: <" + user.getName() + ">(" + user.getID() + ") <" +
          // clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): " +
          // m, false, user1);
          // user.getServer().announce("<" + clientHandler.getUser().getName() + "> (" +
          // clientHandler.getUser().getID() + "): " + m, false, user);
          clientHandler.user.game?.announce(
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
                clientHandler.user.game!!.announce("User not found!", clientHandler.user)
                return
              }
              if (user.game != clientHandler.user.game) {
                clientHandler.user.game!!.announce("User not in this game!", clientHandler.user)
                return
              }
              if (user === clientHandler.user) {
                clientHandler.user.game!!.announce(
                  "You can't private message yourself!",
                  clientHandler.user
                )
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
                    clientHandler.user.game!!.announce(
                      "Private Message Denied: Illegal characters in message",
                      clientHandler.user
                    )
                    return
                  }
                  i++
                }
                if (m.length > 320) {
                  logger.atWarning().log("%s /msg denied: Message Length > 320", user)
                  clientHandler.user.game!!.announce(
                    "Private Message Denied: Message Too Long",
                    clientHandler.user
                  )
                  return
                }
              }

              // user1.getServer().announce("TO: <" + user.getName() + ">(" + user.getID() + ") <" +
              // clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): "
              // + m, false, user1);
              // user.getServer().announce("<" + clientHandler.getUser().getName() + "> (" +
              // clientHandler.getUser().getID() + "): " + m, false, user);
              clientHandler.user.game?.announce(
                "TO: <${user.name}>(${user.id}) <${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                clientHandler.user
              )
              user.game?.announce(
                "<${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                user
              )
              return
            } catch (e1: Exception) {
              clientHandler.user.game!!.announce(
                "Private Message Error: /msg <UserID> <message>",
                clientHandler.user
              )
              return
            }
          } else {
            clientHandler.user.game!!.announce(
              "Private Message Error: /msg <UserID> <message>",
              clientHandler.user
            )
            return
          }
        }
      } else if (message.message == "/ignoreall") {
        try {
          clientHandler.user.ignoreAll = true
          clientHandler.user.server.announce(
            clientHandler.user.name + " is now ignoring everyone!",
            false,
            null
          )
        } catch (e: Exception) {}
        return
      } else if (message.message == "/unignoreall") {
        try {
          clientHandler.user.ignoreAll = false
          clientHandler.user.server.announce(
            clientHandler.user.name + " is now unignoring everyone!",
            false,
            null
          )
        } catch (e: Exception) {}
        return
      } else if (message.message.startsWith("/ignore")) {
        val scanner = Scanner(message.message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            clientHandler.user.game!!.announce("User not found!", clientHandler.user)
            return
          }
          if (user === clientHandler.user) {
            clientHandler.user.game!!.announce("You can't ignore yourself!", clientHandler.user)
            return
          }
          if (clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            clientHandler.user.game!!.announce(
              "You can't ignore a user that is already ignored!",
              clientHandler.user
            )
            return
          }
          if (user.accessLevel >= AccessManager.ACCESS_MODERATOR) {
            clientHandler.user.game!!.announce(
              "You cannot ignore a moderator or admin!",
              clientHandler.user
            )
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
          clientHandler.user.game!!.announce(
            "Ignore User Error: /ignore <UserID>",
            clientHandler.user
          )
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
      } else if (message.message.startsWith("/unignore")) {
        val scanner = Scanner(message.message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            clientHandler.user.game!!.announce("User Not Found!", clientHandler.user)
            return
          }
          if (!clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            clientHandler.user.game!!.announce(
              "You can't unignore a user that isn't ignored",
              clientHandler.user
            )
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
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!")
              )
            } catch (e: Exception) {}
          return
        } catch (e: NoSuchElementException) {
          clientHandler.user.game!!.announce(
            "Unignore User Error: /ignore <UserID>",
            clientHandler.user
          )
          logger
            .atInfo()
            .withCause(e)
            .log(
              "UNIGNORE USER ERROR: %s: %s",
              clientHandler.user.name,
              clientHandler.remoteSocketAddress!!.hostName
            )
          return
        }
      } else if (message.message.startsWith("/me")) {
        val space = message.message.indexOf(' ')
        if (space < 0) {
          clientHandler.user.game!!.announce("Invalid # of Fields!", clientHandler.user)
          return
        }
        var announcement = message.message.substring(space + 1)
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
          clientHandler.user.game!!.announce("You are silenced!", clientHandler.user)
          return
        }
        if (clientHandler.user.server.checkMe(clientHandler.user, announcement)) {
          val m = announcement
          announcement = "*" + clientHandler.user.name + " " + m
          for (user in clientHandler.user.game!!.players) {
            user.game!!.announce(announcement, user)
          }
          return
        }
      } else if (message.message == "/help") {
        clientHandler.user.game!!.announce(
          "/me <message> to make personal message eg. /me is bored ...SupraFast is bored.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        clientHandler.user.game!!.announce(
          "/msg <UserID> <msg> to PM somebody. /msgoff or /msgon to turn pm off | on.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        clientHandler.user.game!!.announce(
          "/ignore <UserID> or /unignore <UserID> or /ignoreall or /unignoreall to ignore users.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
        clientHandler.user.game!!.announce(
          "/p2pon or /p2poff this option ignores all server activity during gameplay.",
          clientHandler.user
        )
        threadSleep(20.milliseconds)
      } else if (message.message == "/stop") {
        if (lookingForGameReporter.cancelActionsForUser(clientHandler.user.id)) {
          clientHandler.user.game!!.announce(
            EmuLang.getStringOrDefault(
              "KailleraServerImpl.CanceledPendingTweet",
              default = "Canceled pending tweet."
            ),
            clientHandler.user
          )
        } else {
          clientHandler.user.game!!.announce("No pending tweets.", clientHandler.user)
        }
      } else
        clientHandler.user.game!!.announce(
          "Unknown Command: " + message.message,
          clientHandler.user
        )
    } else {
      clientHandler.user.game!!.announce("Denied: Flood Control", clientHandler.user)
    }
  }

  override fun handleEvent(gameChatEvent: GameChatEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    try {
      if (
        clientHandler.user.searchIgnoredUsers(
          gameChatEvent.user.connectSocketAddress.address.hostAddress
        )
      )
        return
      else if (clientHandler.user.ignoreAll) {
        if (
          gameChatEvent.user.accessLevel < AccessManager.ACCESS_ADMIN &&
            gameChatEvent.user !== clientHandler.user
        )
          return
      }
      val m = gameChatEvent.message
      clientHandler.send(
        GameChatNotification(clientHandler.nextMessageNumber, gameChatEvent.user.name!!, m)
      )
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct GameChat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val ADMIN_COMMAND_ESCAPE_STRING = "/"
  }
}
