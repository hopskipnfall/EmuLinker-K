package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.GameChat
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.exception.ActionException
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil.threadSleep

class GameOwnerCommandAction : V086Action<GameChat> {
  override val actionPerformedCount = 0

  override fun toString() = "GameOwnerCommandAction"

  fun isValidCommand(chat: String): Boolean =
    chat.startsWith(COMMAND_HELP) ||
      chat.startsWith(COMMAND_DETECTAUTOFIRE) ||
      chat.startsWith(COMMAND_MAXUSERS) ||
      chat.startsWith(COMMAND_MAXPING) ||
      chat == COMMAND_START ||
      chat.startsWith(COMMAND_STARTN) ||
      chat.startsWith(COMMAND_MUTE) ||
      chat.startsWith(COMMAND_EMU) ||
      chat.startsWith(COMMAND_CONN) ||
      chat.startsWith(COMMAND_UNMUTE) ||
      chat.startsWith(COMMAND_SWAP) ||
      chat.startsWith(COMMAND_KICK) ||
      chat.startsWith(COMMAND_SAMEDELAY) ||
      chat.startsWith(COMMAND_NUM)

  @Throws(FatalActionException::class)
  override fun performAction(message: GameChat, clientHandler: V086ClientHandler) {
    val chat = message.message
    val user = clientHandler.user
    val game =
      user.game ?: throw FatalActionException("GameOwner Command Failed: Not in a game: $chat")
    if (user != game.owner && user.accessLevel < AccessManager.ACCESS_SUPERADMIN) {
      if (!chat.startsWith(COMMAND_HELP)) {
        logger
          .atWarning()
          .log("GameOwner Command Denied: Not game owner: %s: %s: %s", game, user, chat)
        game.announce("GameOwner Command Error: You are not an owner!", user)
        return
      }
    }
    try {
      when {
        chat.startsWith(COMMAND_HELP) -> processHelp(game, user)
        chat.startsWith(COMMAND_DETECTAUTOFIRE) -> processDetectAutoFire(chat, game, user)
        chat.startsWith(COMMAND_MAXUSERS) -> processMaxUsers(chat, game, user)
        chat.startsWith(COMMAND_MAXPING) -> processMaxPing(chat, game, user)
        chat == COMMAND_START -> processStart(game, user)
        chat.startsWith(COMMAND_STARTN) -> processStartN(chat, game, user, clientHandler)
        chat.startsWith(COMMAND_MUTE) -> processMute(chat, game, user, clientHandler)
        chat.startsWith(COMMAND_EMU) -> processEmu(chat, game, user, clientHandler)
        chat.startsWith(COMMAND_CONN) -> processConn(chat, game, user)
        chat.startsWith(COMMAND_UNMUTE) -> processUnmute(chat, game, user, clientHandler)
        chat.startsWith(COMMAND_SWAP) -> processSwap(chat, game, user)
        chat.startsWith(COMMAND_KICK) -> processKick(chat, game, user, clientHandler)
        chat.startsWith(COMMAND_SAMEDELAY) -> processSameDelay(chat, game, user)
        chat.startsWith(COMMAND_NUM) -> processNum(game, user)
        else -> {
          game.announce("Unknown Command: $chat", user)
          logger.atInfo().log("Unknown GameOwner Command: %s: %s: %s", game, user, chat)
        }
      }
    } catch (e: ActionException) {
      logger.atInfo().withCause(e).log("GameOwner Command Failed: %s: %s: %s", game, user, chat)
      game.announce(EmuLang.getString("GameOwnerCommandAction.CommandFailed", e.message), user)
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct message")
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processHelp(game: KailleraGameImpl, admin: KailleraUser) {
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
    game.announce("/mute /unmute  <UserID> or /muteall or /unmuteall to mute player(s).", admin)
    threadSleep(20.milliseconds)
    game.announce(
      "/swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
      admin,
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
      admin,
    )
    threadSleep(20.milliseconds)
    game.announce(
      "/samedelay {true | false} to play at the same delay as player with highest ping. Default is false.",
      admin,
    )
    threadSleep(20.milliseconds)
    game.announce(
      "/samedelay {true | false} to play at the same delay as player with highest ping. Default is false.",
      admin,
    )
    threadSleep(20.milliseconds)
  }

  private fun autoFireHelp(game: KailleraGameImpl, admin: KailleraUser) {
    val cur = game.autoFireDetector.sensitivity
    game.announce(EmuLang.getString("GameOwnerCommandAction.HelpSensitivity"), admin)
    threadSleep(20.milliseconds)
    game.announce(EmuLang.getString("GameOwnerCommandAction.HelpDisable"), admin)
    threadSleep(20.milliseconds)
    game.announce(
      EmuLang.getString("GameOwnerCommandAction.HelpCurrentSensitivity", cur) +
        if (cur == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled") else "",
      admin,
    )
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processDetectAutoFire(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    if (game.status != GameStatus.WAITING) {
      game.announce(EmuLang.getString("GameOwnerCommandAction.AutoFireChangeDeniedInGame"), admin)
      return
    }
    val st = StringTokenizer(message, " ")
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
        if (sensitivity == 0) EmuLang.getString("GameOwnerCommandAction.HelpDisabled") else ""
    )
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processEmu(
    message: String,
    game: KailleraGameImpl,
    admin: KailleraUser,
    clientHandler: V086ClientHandler,
  ) {
    var emu = game.owner.clientType
    if (message == "/setemu any") {
      emu = "any"
    }
    admin.game!!.aEmulator = emu!!
    admin.game!!.announce("Owner has restricted the emulator to: $emu")
    return
  }

  // new gameowner command /setconn
  @Throws(ActionException::class, MessageFormatException::class)
  private fun processConn(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    var conn = game.owner.connectionType.readableName
    if (message == "/setconn any") {
      conn = "any"
    }
    admin.game!!.aConnection = conn
    admin.game!!.announce("Owner has restricted the connection type to: $conn")
    return
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processNum(game: KailleraGameImpl, admin: KailleraUser) {
    admin.game!!.announce("${game.players.size} in the room!", admin)
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processSameDelay(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    if (message == "/samedelay true") {
      game.sameDelay = true
      admin.game!!.announce("Players will have the same delay when game starts (restarts)!")
    } else {
      game.sameDelay = false
      admin.game!!.announce("Players will have independent delays when game starts (restarts)!")
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processMute(
    message: String,
    game: KailleraGameImpl,
    admin: KailleraUser,
    clientHandler: V086ClientHandler,
  ) {
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      val str = scanner.next()
      if (str == "/muteall") {
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
        admin.game!!.announce("All players have been muted!")
        return
      }
      val userID = scanner.nextInt()
      val user = clientHandler.user.server.getUser(userID)
      if (user == null) {
        admin.game!!.announce("Player doesn't exist!", admin)
        return
      }
      if (user === clientHandler.user) {
        user.game!!.announce("You can't mute yourself!", admin)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          admin.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        user.game!!.announce("You can't mute an Admin", admin)
        return
      }

      // mute by IP
      game.mutedUsers.add(user.connectSocketAddress.address.hostAddress)
      user.isMuted = true
      val user1 = clientHandler.user
      user1.game!!.announce(user.name + " has been muted!")
    } catch (e: NoSuchElementException) {
      val user = clientHandler.user
      user.game!!.announce("Mute Player Error: /mute <UserID>", admin)
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processUnmute(
    message: String,
    game: KailleraGameImpl,
    admin: KailleraUser,
    clientHandler: V086ClientHandler,
  ) {
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      val str = scanner.next()
      if (str == "/unmuteall") {
        game.players.forEach { kailleraUser ->
          kailleraUser.isMuted = false
          game.mutedUsers.remove(kailleraUser.connectSocketAddress.address.hostAddress)
        }
        admin.game!!.announce("All players have been unmuted!")
        return
      }
      val userID = scanner.nextInt()
      val user = clientHandler.user.server.getUser(userID)
      if (user == null) {
        admin.game!!.announce("Player doesn't exist!", admin)
        return
      }
      if (user === clientHandler.user) {
        user.game!!.announce("You can't unmute yourself!", admin)
        return
      }
      if (
        user.accessLevel >= AccessManager.ACCESS_ADMIN &&
          admin.accessLevel != AccessManager.ACCESS_SUPERADMIN
      ) {
        user.game!!.announce("You can't unmute an Admin", admin)
        return
      }
      game.mutedUsers.remove(user.connectSocketAddress.address.hostAddress)
      user.isMuted = false
      val user1 = clientHandler.user
      user1.game!!.announce(user.name + " has been unmuted!")
    } catch (e: NoSuchElementException) {
      val user = clientHandler.user
      user.game!!.announce("Unmute Player Error: /unmute <UserID>", admin)
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processStartN(
    message: String,
    game: KailleraGameImpl,
    admin: KailleraUser,
    clientHandler: V086ClientHandler,
  ) {
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      scanner.next()
      val num = scanner.nextInt()
      if (num in 1..100) {
        game.startN = num.toByte().toInt()
        game.announce("This game will start when $num players have joined.")
      } else {
        game.announce("StartN Error: Enter value between 1 and 100.", admin)
      }
    } catch (e: NoSuchElementException) {
      game.announce("Failed: /startn <#>", admin)
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processSwap(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    /*if(game.getStatus() != KailleraGame.STATUS_PLAYING){
    	game.announce("Failed: wap Players can only be used during gameplay!", admin);
    	return;
    }*/
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      var i: Int
      val str: String
      scanner.next()
      val test = scanner.nextInt()
      str = test.toString()
      if (game.players.size < str.length) {
        game.announce("Failed: You can't swap more than the # of players in the room.", admin)
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
            }*/ game.announce(player.name + " is now Player#: " + player.playerNumber)
            i++
          }
        } else
          game.announce(
            "Swap Player Error: /swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
            admin,
          )
      }
    } catch (e: NoSuchElementException) {
      game.announce(
        "Swap Player Error: /swap <order> eg. 123..n {n = total # of players; Each slot = new player#}",
        admin,
      )
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processStart(game: KailleraGameImpl, admin: KailleraUser) {
    game.start(admin)
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processKick(
    message: String,
    game: KailleraGameImpl,
    admin: KailleraUser,
    clientHandler: V086ClientHandler,
  ) {
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      val str = scanner.next()
      if (str == "/kickall") {
        // start kick players from last to first and don't kick owner or admin
        for (w in game.players.size downTo 1) {
          if (
            game.getPlayer(w)!!.accessLevel < AccessManager.ACCESS_ADMIN &&
              game.getPlayer(w) != game.owner
          )
            game.kick(admin, game.getPlayer(w)!!.id)
        }
        admin.game!!.announce("All players have been kicked!")
        return
      }
      val playerNumber = scanner.nextInt()
      if (playerNumber in 1..100) {
        if (game.getPlayer(playerNumber) != null)
          game.kick(admin, game.getPlayer(playerNumber)!!.id)
        else {
          game.announce("Player doesn't exisit!", admin)
        }
      } else {
        game.announce("Kick Player Error: Enter value between 1 and 100", admin)
      }
    } catch (e: NoSuchElementException) {
      game.announce("Failed: /kick <Player#> or /kickall to kick all players.", admin)
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processMaxUsers(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    if (System.currentTimeMillis() - lastMaxUserChange <= 3000) {
      game.announce("Max User Command Spam Detection...Please Wait!", admin)
      lastMaxUserChange = System.currentTimeMillis()
      return
    } else {
      lastMaxUserChange = System.currentTimeMillis()
    }
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      scanner.next()
      val num = scanner.nextInt()
      if (num in 1..100) {
        game.maxUsers = num
        game.announce("Max Users has been set to $num")
      } else {
        game.announce("Max Users Error: Enter value between 1 and 100", admin)
      }
    } catch (e: NoSuchElementException) {
      game.announce("Failed: /maxusers <#>", admin)
    }
  }

  @Throws(ActionException::class, MessageFormatException::class)
  private fun processMaxPing(message: String, game: KailleraGameImpl, admin: KailleraUser) {
    val scanner = Scanner(message).useDelimiter(" ")
    try {
      scanner.next()
      val num = scanner.nextInt()
      if (num in 1..1000) {
        game.maxPing = num
        game.announce("Max Ping has been set to $num")
      } else {
        game.announce("Max Ping Error: Enter value between 1 and 1000", admin)
      }
    } catch (e: NoSuchElementException) {
      game.announce("Failed: /maxping <#>", admin)
    }
  }

  companion object {
    private var lastMaxUserChange: Long = 0
    private val logger = FluentLogger.forEnclosingClass()

    private const val COMMAND_HELP = "/help"

    private const val COMMAND_DETECTAUTOFIRE = "/detectautofire"

    // SF MOD
    private const val COMMAND_MAXUSERS = "/maxusers"

    private const val COMMAND_MAXPING = "/maxping"

    private const val COMMAND_START = "/start"

    private const val COMMAND_STARTN = "/startn"

    private const val COMMAND_MUTE = "/mute"

    private const val COMMAND_UNMUTE = "/unmute"

    private const val COMMAND_SWAP = "/swap"

    private const val COMMAND_KICK = "/kick"

    private const val COMMAND_EMU = "/setemu"

    private const val COMMAND_CONN = "/setconn"

    private const val COMMAND_SAMEDELAY = "/samedelay"

    private const val COMMAND_NUM = "/num"
  }
}
