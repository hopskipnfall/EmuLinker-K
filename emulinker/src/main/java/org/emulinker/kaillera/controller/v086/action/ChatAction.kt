package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.netty.channel.ChannelHandlerContext
import java.util.Locale
import java.util.Scanner
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.ChatNotification
import org.emulinker.kaillera.controller.v086.protocol.ChatRequest
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.model.event.ChatEvent
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil.threadSleep
import org.emulinker.util.EmuUtil.toSimpleUtcDatetime

private const val ADMIN_COMMAND_ESCAPE_STRING = "/"

class ChatAction(private val adminCommandAction: AdminCommandAction) :
  V086Action<ChatRequest>, V086ServerEventHandler<ChatEvent> {
  override fun toString() = "ChatAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: ChatRequest, ctx: ChannelHandlerContext, clientHandler: V086ClientHandler) {
    if (message.message.startsWith(ADMIN_COMMAND_ESCAPE_STRING)) {
      if (clientHandler.user.accessLevel > AccessManager.ACCESS_ELEVATED) {
        try {
          if (adminCommandAction.isValidCommand(message.message)) {
            adminCommandAction.performAction(message, ctx, clientHandler)
            if (message.message == "/help") {
              checkCommands(message, clientHandler, ctx)
            }
          } else {
            checkCommands(message, clientHandler, ctx)
          }
        } catch (e: FatalActionException) {
          logger.atWarning().withCause(e).log("Admin command failed")
        }
        return
      }
      checkCommands(message, clientHandler, ctx)
      return
    }
    try {
      clientHandler.user.chat(message.message)
    } catch (e: ActionException) {
      logger.atInfo().withCause(e).log("Chat Denied: %s: %s", clientHandler.user, message.message)
      try {
        clientHandler.send(
          InformationMessage(
            clientHandler.nextMessageNumber,
            "server",
            EmuLang.getString("ChatAction.ChatDenied", e.message),
          ), ctx
        )
      } catch (e2: MessageFormatException) {
        logger.atSevere().withCause(e2).log("Failed to construct InformationMessage message")
      }
    }
  }

  @Throws(FatalActionException::class)
  private fun checkCommands(chatMessage: ChatRequest, clientHandler: V086ClientHandler, ctx: ChannelHandlerContext) {
    var doCommand = true
    val userN = clientHandler.user
    if (userN.accessLevel < AccessManager.ACCESS_ELEVATED) {
      try {
        clientHandler.user.chat(":USER_COMMAND")
      } catch (e: ActionException) {
        doCommand = false
      }
    }
    if (doCommand) {
      // SF MOD - User Commands
      if (chatMessage.message == "/alivecheck") {
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              ":ALIVECHECK=EmuLinker-K Alive Check: You are still logged in.",
            ), ctx
          )
        } catch (e: Exception) {}
      } else if (
        chatMessage.message == "/version" &&
          clientHandler.user.accessLevel < AccessManager.ACCESS_ADMIN
      ) {
        val releaseInfo = clientHandler.user.server.releaseInfo
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "VERSION: ${releaseInfo.productName}: ${releaseInfo.version}: ${releaseInfo.buildDate.toSimpleUtcDatetime()}",
            ), ctx
          )
        } catch (e: Exception) {}
      } else if (chatMessage.message == "/myip") {
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "Your IP Address is: " + clientHandler.user.connectSocketAddress.address.hostAddress,
            ), ctx
          )
        } catch (e: Exception) {}
      } else if (chatMessage.message == "/msgon") {
        clientHandler.user.isAcceptingDirectMessages = true
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "Private messages are now on.",
            ), ctx
          )
        } catch (e: Exception) {}
      } else if (chatMessage.message == "/msgoff") {
        clientHandler.user.isAcceptingDirectMessages = false
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "Private messages are now off.",
            ), ctx
          )
        } catch (e: Exception) {}
      } else if (chatMessage.message.startsWith("/me")) {
        val space = chatMessage.message.indexOf(' ')
        if (space < 0) {
          try {
            clientHandler.send(
              InformationMessage(clientHandler.nextMessageNumber, "server", "Invalid # of Fields!"), ctx
            )
          } catch (e: Exception) {}
          return
        }
        var announcement = chatMessage.message.substring(space + 1)
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
          try {
            clientHandler.send(
              InformationMessage(clientHandler.nextMessageNumber, "server", "You are silenced!"), ctx
            )
          } catch (e: Exception) {}
          return
        }
        if (clientHandler.user.server.checkMe(clientHandler.user, announcement)) {
          val m = announcement
          announcement = "*" + clientHandler.user.name + " " + m
          val user1 = clientHandler.user
          clientHandler.user.server.announce(announcement, true, user1)
        }
      } else if (chatMessage.message.startsWith("/msg")) {
        val user1 = clientHandler.user
        val scanner = Scanner(chatMessage.message).useDelimiter(" ")
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
          try {
            clientHandler.send(
              InformationMessage(clientHandler.nextMessageNumber, "server", "You are silenced!"), ctx
            )
          } catch (e: Exception) {}
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
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!"), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (user === clientHandler.user) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "You can't private message yourself!",
                ), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (
            !user.isAcceptingDirectMessages ||
              user.searchIgnoredUsers(clientHandler.user.connectSocketAddress.address.hostAddress)
          ) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "<" + user.name + "> Is not accepting private messages!",
                ), ctx
              )
            } catch (e: Exception) {}
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
                try {
                  clientHandler.send(
                    InformationMessage(
                      clientHandler.nextMessageNumber,
                      "server",
                      "Private Message Denied: Illegal characters in message",
                    ), ctx
                  )
                } catch (e: Exception) {}
                return
              }
            }
            if (m.length > 320) {
              logger.atWarning().log("%s /msg denied: Message Length > 320", user)
              try {
                clientHandler.send(
                  InformationMessage(
                    clientHandler.nextMessageNumber,
                    "server",
                    "Private Message Denied: Message Too Long",
                  ), ctx
                )
              } catch (e: Exception) {}
              return
            }
          }
          user1.lastMsgID = user.id
          user.lastMsgID = user1.id
          user1.server.announce(
            "TO: <${user.name}>(${user.id}) <${clientHandler.user.name}> (${clientHandler.user.id}): $m",
            false,
            user1,
          )
          user.server.announce(
            "<${clientHandler.user.name}> (${clientHandler.user.id}): $m",
            false,
            user,
          )

          /*if(user1.getGame() != null){
          	user1.getGame().announce("TO: <" + user.getName() + ">(" + user.getID() + ") <" + clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): " + m, user1);
          }

          if(user.getGame() != null){
          	user.getGame().announce("<" + clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): " + m, user);
          }*/
        } catch (e: NoSuchElementException) {
          if (user1.lastMsgID != -1) {
            try {
              val user = clientHandler.user.server.getUser(user1.lastMsgID)
              val sb = StringBuilder()
              while (scanner.hasNext()) {
                sb.append(scanner.next())
                sb.append(" ")
              }
              if (user == null) {
                try {
                  clientHandler.send(
                    InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!"), ctx
                  )
                } catch (e1: Exception) {}
                return
              }
              if (user === clientHandler.user) {
                try {
                  clientHandler.send(
                    InformationMessage(
                      clientHandler.nextMessageNumber,
                      "server",
                      "You can't private message yourself!",
                    ), ctx
                  )
                } catch (e1: Exception) {}
                return
              }
              if (!user.isAcceptingDirectMessages) {
                try {
                  clientHandler.send(
                    InformationMessage(
                      clientHandler.nextMessageNumber,
                      "server",
                      "<" + user.name + "> Is not accepting private messages!",
                    ), ctx
                  )
                } catch (e1: Exception) {}
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
                    try {
                      clientHandler.send(
                        InformationMessage(
                          clientHandler.nextMessageNumber,
                          "server",
                          "Private Message Denied: Illegal characters in message",
                        ), ctx
                      )
                    } catch (e1: Exception) {}
                    return
                  }
                  i++
                }
                if (m.length > 320) {
                  logger.atWarning().log("%s /msg denied: Message Length > 320", user)
                  try {
                    clientHandler.send(
                      InformationMessage(
                        clientHandler.nextMessageNumber,
                        "server",
                        "Private Message Denied: Message Too Long",
                      ), ctx
                    )
                  } catch (e1: Exception) {}
                  return
                }
              }
              user1.server.announce(
                "TO: <${user.name}>(${user.id}) <${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                false,
                user1,
              )
              user.server.announce(
                "<${clientHandler.user.name}> (${clientHandler.user.id}): $m",
                false,
                user,
              )
              /*if(user1.getGame() != null){
              	user1.getGame().announce("TO: <" + user.getName() + ">(" + user.getID() + ") <" + clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): " + m, user1);
              }

              if(user.getGame() != null){
              	user.getGame().announce("<" + clientHandler.getUser().getName() + "> (" + clientHandler.getUser().getID() + "): " + m, user);
              }*/
            } catch (e1: NoSuchElementException) {
              try {
                clientHandler.send(
                  InformationMessage(
                    clientHandler.nextMessageNumber,
                    "server",
                    "Private Message Error: /msg <UserID> <message>",
                  ), ctx
                )
              } catch (e2: Exception) {}
              return
            }
          } else {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "Private Message Error: /msg <UserID> <message>",
                ), ctx
              )
            } catch (e1: Exception) {}
            return
          }
        }
      } else if (chatMessage.message == "/ignoreall") {
        val user = clientHandler.user
        try {
          clientHandler.user.ignoreAll = true
          user.server.announce(clientHandler.user.name + " is now ignoring everyone!", false)
        } catch (e: Exception) {}
      } else if (chatMessage.message == "/unignoreall") {
        val user = clientHandler.user
        try {
          clientHandler.user.ignoreAll = false
          user.server.announce(clientHandler.user.name + " is now unignoring everyone!", false)
        } catch (e: Exception) {}
      } else if (chatMessage.message.startsWith("/ignore")) {
        val scanner = Scanner(chatMessage.message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!"), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (user === clientHandler.user) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "You can't ignore yourself!",
                ), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "You can't ignore a user that is already ignored!",
                ), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (user.accessLevel >= AccessManager.ACCESS_MODERATOR) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "You cannot ignore a moderator or admin!",
                ), ctx
              )
            } catch (e: Exception) {}
            return
          }
          clientHandler.user.addIgnoredUser(user.connectSocketAddress.address.hostAddress)
          user.server.announce(
            clientHandler.user.name + " is now ignoring <" + user.name + "> ID: " + user.id,
            false,
          )
        } catch (e: NoSuchElementException) {
          val user = clientHandler.user
          user.server.announce("Ignore User Error: /ignore <UserID>", false, user)
          logger
            .atInfo()
            .log(
              "IGNORE USER ERROR: %s: %s",
              user.name,
              clientHandler.remoteSocketAddress!!.hostName,
            )
          return
        }
      } else if (chatMessage.message.startsWith("/unignore")) {
        val scanner = Scanner(chatMessage.message).useDelimiter(" ")
        try {
          scanner.next()
          val userID = scanner.nextInt()
          val user = clientHandler.user.server.getUser(userID)
          if (user == null) {
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!"), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (!clientHandler.user.findIgnoredUser(user.connectSocketAddress.address.hostAddress)) {
            try {
              clientHandler.send(
                InformationMessage(
                  clientHandler.nextMessageNumber,
                  "server",
                  "You can't unignore a user that isn't ignored!",
                ), ctx
              )
            } catch (e: Exception) {}
            return
          }
          if (
            clientHandler.user.removeIgnoredUser(
              user.connectSocketAddress.address.hostAddress,
              false,
            )
          )
            user.server.announce(
              clientHandler.user.name + " is now unignoring <" + user.name + "> ID: " + user.id,
              gamesAlso = false,
            )
          else
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", "User Not Found!"), ctx
              )
            } catch (e: Exception) {}
        } catch (e: NoSuchElementException) {
          clientHandler.user.server.announce(
            "Unignore User Error: /ignore <UserID>",
            false,
            clientHandler.user,
          )
          logger
            .atInfo()
            .withCause(e)
            .log(
              "UNIGNORE USER ERROR: %s: %s",
              clientHandler.user.name,
              clientHandler.remoteSocketAddress,
            )
          return
        }
      } else if (chatMessage.message == "/help") {
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "/me <message> to make personal message eg. /me is bored ...SupraFast is bored.",
            ), ctx
          )
        } catch (e: Exception) {}
        threadSleep(20.milliseconds)
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "/ignore <UserID> or /unignore <UserID> or /ignoreall or /unignoreall to ignore users.",
            ), ctx
          )
        } catch (e: Exception) {}
        threadSleep(20.milliseconds)
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "/msg <UserID> <msg> to PM somebody. /msgoff or /msgon to turn pm off | on.",
            ), ctx
          )
        } catch (e: Exception) {}
        threadSleep(20.milliseconds)
        try {
          clientHandler.send(
            InformationMessage(
              clientHandler.nextMessageNumber,
              "server",
              "/myip to get your IP Address.",
            ), ctx
          )
        } catch (e: Exception) {}
        threadSleep(20.milliseconds)
        if (clientHandler.user.accessLevel == AccessManager.ACCESS_MODERATOR) {
          try {
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "/silence <UserID> <min> to silence a user. 15min max.",
              ), ctx
            )
          } catch (e: Exception) {}
          threadSleep(20.milliseconds)
          try {
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "/kick <UserID> to kick a user.",
              ), ctx
            )
          } catch (e: Exception) {}
          threadSleep(20.milliseconds)
        }
        if (clientHandler.user.accessLevel < AccessManager.ACCESS_ADMIN) {
          try {
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "/version to get server version.",
              ), ctx
            )
          } catch (e: Exception) {}
          threadSleep(20.milliseconds)
          try {
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "/finduser <Nick> to get a user's info. eg. /finduser sup ...will return SupraFast info.",
              ), ctx
            )
          } catch (e: Exception) {}
          threadSleep(20.milliseconds)
          return
        }
      } else if (
        chatMessage.message.startsWith("/finduser") &&
          clientHandler.user.accessLevel < AccessManager.ACCESS_ADMIN
      ) {
        val space = chatMessage.message.indexOf(' ')
        if (space < 0) {
          try {
            clientHandler.send(
              InformationMessage(
                clientHandler.nextMessageNumber,
                "server",
                "Finduser Error: /finduser <nick> eg. /finduser sup ...will return SupraFast info.",
              ), ctx
            )
          } catch (e: Exception) {}
          return
        }
        var foundCount = 0
        val str = chatMessage.message.substring(space + 1)
        // WildcardStringPattern pattern = new WildcardStringPattern
        for (user in clientHandler.user.users) {
          if (!user.loggedIn) continue
          if (
            user.name!!.lowercase(Locale.getDefault()).contains(str.lowercase(Locale.getDefault()))
          ) {
            val sb = StringBuilder()
            sb.append("UserID: ")
            sb.append(user.id)
            sb.append(", Nick: <")
            sb.append(user.name)
            sb.append(">")
            sb.append(", Access: ")
            if (user.accessStr == "SuperAdmin" || user.accessStr == "Admin") {
              sb.append("Normal")
            } else {
              sb.append(user.accessStr)
            }
            if (user.game != null) {
              sb.append(", GameID: ")
              sb.append(user.game!!.id)
              sb.append(", Game: ")
              sb.append(user.game!!.romName)
            }
            try {
              clientHandler.send(
                InformationMessage(clientHandler.nextMessageNumber, "server", sb.toString()), ctx
              )
            } catch (e: Exception) {}
            foundCount++
          }
        }
        if (foundCount == 0)
          try {
            clientHandler.send(
              InformationMessage(clientHandler.nextMessageNumber, "server", "No Users Found!"), ctx
            )
          } catch (e: Exception) {}
      } else userN.server.announce("Unknown Command: " + chatMessage.message, false, userN)
    } else {
      userN.server.announce("Denied: Flood Control", false, userN)
    }
  }

  override fun handleEvent(event: ChatEvent, clientHandler: V086ClientHandler, ctx: ChannelHandlerContext) {
    try {
      if (
        clientHandler.user.searchIgnoredUsers(event.user.connectSocketAddress.address.hostAddress)
      )
        return
      else if (clientHandler.user.ignoreAll) {
        if (
          event.user.accessLevel < AccessManager.ACCESS_ADMIN && event.user !== clientHandler.user
        )
          return
      }
      val m = event.message
      clientHandler.send(ChatNotification(clientHandler.nextMessageNumber, event.user.name!!, m), ctx)
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct Chat.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
