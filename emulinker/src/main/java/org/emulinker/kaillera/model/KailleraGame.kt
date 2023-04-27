package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Throws
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.event.AllReadyEvent
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameEvent
import org.emulinker.kaillera.model.event.GameInfoEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.GameStatusChangedEvent
import org.emulinker.kaillera.model.event.GameTimeoutEvent
import org.emulinker.kaillera.model.event.PlayerDesynchEvent
import org.emulinker.kaillera.model.event.UserDroppedGameEvent
import org.emulinker.kaillera.model.event.UserJoinedGameEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.DropGameException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.exception.GameDataException
import org.emulinker.kaillera.model.exception.GameKickException
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.exception.UserReadyException
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.emulinker.kaillera.model.impl.KailleraServerImpl
import org.emulinker.kaillera.model.impl.PlayerActionQueue
import org.emulinker.kaillera.model.impl.PlayerTimeoutException
import org.emulinker.util.EmuLang

class KailleraGame(
  val id: Int,
  val romName: String,
  val owner: KailleraUser,
  val server: KailleraServerImpl,
  val bufferSize: Int,
) {
  var highestUserFrameDelay = 0
  var maxPing = 1000
  var startN = -1
  var ignoringUnnecessaryServerActivity = false
  /** Frame delay is synced with others users in the same game (see /samedelay). */
  var sameDelay = false
  var startTimeout = false
  var maxUsers = 8
    set(maxUsers) {
      field = maxUsers
      server.addEvent(GameStatusChangedEvent(server, this))
    }
  var startTimeoutTime: Long = 0
    private set
  val players: MutableList<KailleraUser> = CopyOnWriteArrayList()

  val mutedUsers: MutableList<String> = mutableListOf()
  var aEmulator = "any"
  var aConnection = "any"
  val startDate: Date = Date()
  @JvmField var swap = false

  var status = GameStatus.WAITING
    private set(status) {
      field = status
      server.addEvent(GameStatusChangedEvent(server, this))
    }

  private val toString =
    "Game$id(${if (romName.length > 15) romName.substring(0, 15) + "..." else romName})"
  private var lastAddress = "null"
  private var lastAddressCount = 0
  private var isSynched = false

  private val timeoutMillis = 100
  private val desynchTimeouts = 120

  private val statsCollector: StatsCollector? = server.statsCollector
  private val kickedUsers: MutableList<String> = ArrayList()

  private val actionsPerMessage = owner.connectionType.byteValue.toInt()

  var playerActionQueue: Array<PlayerActionQueue>? = null
    private set

  val clientType: String?
    get() = owner.clientType

  var autoFireDetector: AutoFireDetector = server.getAutoFireDetector(this)

  fun getPlayerNumber(user: KailleraUser): Int {
    return players.indexOf(user) + 1
  }

  fun getPlayer(playerNumber: Int): KailleraUser? {
    if (playerNumber > players.size) {
      logger
        .atSevere()
        .log("%s: getPlayer(%d) failed! (size = %d)", this, playerNumber, players.size)
      return null
    }
    return players[playerNumber - 1]
  }

  override fun toString() = toString

  fun toDetailedString(): String {
    return "KailleraGame[id=$id romName=$romName owner=$owner numPlayers=${players.size} status=$status]"
  }

  private val playingCount: Int
    get() = players.asSequence().filter { it.status == UserStatus.PLAYING }.count()

  private val synchedCount: Int
    get() = playerActionQueue?.count { it.synched } ?: 0

  private fun addEvent(event: GameEvent) {
    players.forEach { it.addEvent(event) }
  }

  @Synchronized
  @Throws(GameChatException::class)
  fun chat(user: KailleraUser, message: String) {
    if (!players.contains(user)) {
      logger.atWarning().log("%s game chat denied: not in %s", user, this)
      throw GameChatException(EmuLang.getString("KailleraGame.GameChatErrorNotInGame"))
    }
    if (user.accessLevel == AccessManager.ACCESS_NORMAL) {
      if (server.maxGameChatLength > 0 && message.length > server.maxGameChatLength) {
        logger
          .atWarning()
          .log("%s gamechat denied: Message Length > %d", user, server.maxGameChatLength)
        addEvent(
          GameInfoEvent(this, EmuLang.getString("KailleraGame.GameChatDeniedMessageTooLong"), user)
        )
        throw GameChatException(EmuLang.getString("KailleraGame.GameChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s, %s gamechat: %s", user, this, message)
    addEvent(GameChatEvent(this, user, message))
  }

  @Synchronized
  fun announce(announcement: String, toUser: KailleraUser? = null) {
    addEvent(GameInfoEvent(this, announcement, toUser))
  }

  @Synchronized
  @Throws(GameKickException::class)
  fun kick(requester: KailleraUser, userID: Int) {
    if (requester.accessLevel < AccessManager.ACCESS_ADMIN) {
      if (requester != owner) {
        logger.atWarning().log("%s kick denied: not the owner of %s", requester, this)
        throw GameKickException(EmuLang.getString("KailleraGame.GameKickDeniedNotGameOwner"))
      }
    }
    if (requester.userData.id == userID) {
      logger.atWarning().log("%s kick denied: attempt to kick self", requester)
      throw GameKickException(EmuLang.getString("KailleraGame.GameKickDeniedCannotKickSelf"))
    }
    for (player in players) {
      if (player.userData.id == userID) {
        try {
          if (requester.accessLevel != AccessManager.ACCESS_SUPERADMIN) {
            if (player.accessLevel >= AccessManager.ACCESS_ADMIN) {
              return
            }
          }
          logger.atInfo().log("%s kicked: %d from %s", requester, userID, this)
          // SF MOD - Changed to IP rather than ID
          kickedUsers.add(player.connectSocketAddress.address.hostAddress)
          player.quitGame()
          return
        } catch (e: Exception) {
          // this shouldn't happen
          logger
            .atSevere()
            .withCause(e)
            .log("Caught exception while making user quit game! This shouldn't happen!")
        }
      }
    }
    logger.atWarning().log("%s kick failed: user %d not found in: %s", requester, userID, this)
    throw GameKickException(EmuLang.getString("KailleraGame.GameKickErrorUserNotFound"))
  }

  @Throws(JoinGameException::class)
  suspend fun join(user: KailleraUser): Int {
    val access = server.accessManager.getAccess(user.socketAddress.address)

    // SF MOD - Join room spam protection
    if (lastAddress == user.connectSocketAddress.address.hostAddress) {
      lastAddressCount++
      if (lastAddressCount >= 4) {
        logger.atInfo().log("%s join spam protection: %d from %s", user, user.userData.id, this)
        // SF MOD - Changed to IP rather than ID
        if (access < AccessManager.ACCESS_ADMIN) {
          kickedUsers.add(user.connectSocketAddress.address.hostAddress)
          try {
            user.quitGame()
          } catch (e: Exception) {}
          throw JoinGameException("Spam Protection")
        }
      }
    } else {
      lastAddressCount = 0
      lastAddress = user.connectSocketAddress.address.hostAddress
    }
    if (players.contains(user)) {
      logger.atWarning().log("%s join game denied: already in %s", user, this)
      throw JoinGameException(EmuLang.getString("KailleraGame.JoinGameErrorAlreadyInGame"))
    }
    if (access < AccessManager.ACCESS_ELEVATED && players.size >= maxUsers) {
      logger.atWarning().log("%s join game denied: max users reached %s", user, this)
      throw JoinGameException("This room's user capacity has been reached.")
    }
    if (access < AccessManager.ACCESS_ELEVATED && user.ping > maxPing) {
      logger.atWarning().log("%s join game denied: max ping reached %s", user, this)
      throw JoinGameException("Your ping is too high for this room.")
    }
    if (access < AccessManager.ACCESS_ELEVATED && aEmulator != "any") {
      if (aEmulator != user.clientType) {
        logger
          .atWarning()
          .log("%s join game denied: owner doesn't allow that emulator: %s", user, user.clientType)
        throw JoinGameException("Owner only allows emulator version: $aEmulator")
      }
    }
    if (access < AccessManager.ACCESS_ELEVATED && aConnection != "any") {
      if (user.connectionType != owner.connectionType) {
        logger
          .atWarning()
          .log(
            "%s join game denied: owner doesn't allow that connection type: %s",
            user,
            user.connectionType
          )
        throw JoinGameException("Owner only allows connection type: ${owner.connectionType}")
      }
    }
    if (
      access < AccessManager.ACCESS_ADMIN &&
        kickedUsers.contains(user.connectSocketAddress.address.hostAddress)
    ) {
      logger.atWarning().log("%s join game denied: previously kicked: %s", user, this)
      throw JoinGameException(EmuLang.getString("KailleraGame.JoinGameDeniedPreviouslyKicked"))
    }
    if (access == AccessManager.ACCESS_NORMAL && status != GameStatus.WAITING) {
      logger
        .atWarning()
        .log("%s join game denied: attempt to join game in progress: %s", user, this)
      throw JoinGameException(EmuLang.getString("KailleraGame.JoinGameDeniedGameIsInProgress"))
    }
    if (mutedUsers.contains(user.connectSocketAddress.address.hostAddress)) {
      user.isMuted = true
    }
    players.add(user)
    user.playerNumber = players.size
    server.addEvent(GameStatusChangedEvent(server, this))
    logger.atInfo().log("%s joined: %s", user, this)
    addEvent(UserJoinedGameEvent(this, user))

    // SF MOD - /startn
    if (startN != -1) {
      if (players.size >= startN) {
        delay(1.seconds)
        try {
          start(owner)
        } catch (e: Exception) {}
      }
    }

    // TODO(nue): Localize this welcome message?
    // announce(
    //     "Help: "
    //         + getServer().getReleaseInfo().getProductName()
    //         + " v"
    //         + getServer().getReleaseInfo().getVersionString()
    //         + ": "
    //         + getServer().getReleaseInfo().getReleaseDate()
    //         + " - Visit: www.EmuLinker.org",
    //     user);
    // announce("************************", user);
    // announce("Type /p2pon to ignore ALL server activity during gameplay.", user);
    // announce("This will reduce lag that you contribute due to a busy server.", user);
    // announce("If server is greater than 60 users, option is auto set.", user);
    // announce("************************", user);

    /*
    if(autoFireDetector != null)
    {
    	if(autoFireDetector.getSensitivity() > 0)
    	{
    		announce(EmuLang.getString("KailleraGame.AutofireDetectionOn"));
    		announce(EmuLang.getString("KailleraGame.AutofireCurrentSensitivity", autoFireDetector.getSensitivity()));
    	}
    	else
    	{
    		announce(EmuLang.getString("KailleraGame.AutofireDetectionOff"));
    	}
    	announce(EmuLang.getString("KailleraGame.GameHelp"));
    }
    */
    // }

    // new SF MOD - different emulator versions notifications
    if (
      access < AccessManager.ACCESS_ADMIN &&
        user.clientType != owner.clientType &&
        !owner.game!!.romName.startsWith("*")
    )
      addEvent(
        GameInfoEvent(
          this,
          "${user.userData.name} using different emulator version: ${user.clientType}"
        )
      )
    return players.indexOf(user) + 1
  }

  @Synchronized
  @Throws(StartGameException::class)
  fun start(user: KailleraUser) {
    val access = server.accessManager.getAccess(user.socketAddress.address)
    if (user != owner && access < AccessManager.ACCESS_ADMIN) {
      logger.atWarning().log("%s start game denied: not the owner of %s", user, this)
      throw StartGameException(EmuLang.getString("KailleraGame.StartGameDeniedOnlyOwnerMayStart"))
    }
    if (status == GameStatus.SYNCHRONIZING) {
      logger.atWarning().log("%s start game failed: %s status is %s", user, this, status)
      throw StartGameException(EmuLang.getString("KailleraGame.StartGameErrorSynchronizing"))
    } else if (status == GameStatus.PLAYING) {
      logger.atWarning().log("%s start game failed: %s status is %s", user, this, status)
      throw StartGameException(EmuLang.getString("KailleraGame.StartGameErrorStatusIsPlaying"))
    }
    if (access == AccessManager.ACCESS_NORMAL && players.size < 2 && !server.allowSinglePlayer) {
      logger.atWarning().log("%s start game denied: %s needs at least 2 players", user, this)
      throw StartGameException(
        EmuLang.getString("KailleraGame.StartGameDeniedSinglePlayerNotAllowed")
      )
    }

    // do not start if not game
    if (owner.game!!.romName.startsWith("*")) return
    for (player in players) {
      if (!player.inStealthMode) {
        if (player.connectionType != owner.connectionType) {
          logger
            .atWarning()
            .log(
              "%s start game denied: %s: All players must use the same connection type",
              user,
              this
            )
          addEvent(
            GameInfoEvent(
              this,
              EmuLang.getString(
                "KailleraGame.StartGameConnectionTypeMismatchInfo",
                owner.connectionType
              )
            )
          )
          throw StartGameException(
            EmuLang.getString("KailleraGame.StartGameDeniedConnectionTypeMismatch")
          )
        }
        if (player.clientType != clientType) {
          logger
            .atWarning()
            .log("%s start game denied: %s: All players must use the same emulator!", user, this)
          addEvent(
            GameInfoEvent(
              this,
              EmuLang.getString("KailleraGame.StartGameEmulatorMismatchInfo", clientType)
            )
          )
          throw StartGameException(
            EmuLang.getString("KailleraGame.StartGameDeniedEmulatorMismatch")
          )
        }
      }
    }
    logger.atInfo().log("%s started: %s", user, this)
    status = GameStatus.SYNCHRONIZING
    autoFireDetector.start(players.size)
    val actionQueueBuilder: Array<PlayerActionQueue?> = arrayOfNulls(players.size)
    startTimeout = false
    highestUserFrameDelay = 1
    if (server.users.size > 60) {
      ignoringUnnecessaryServerActivity = true
    }
    for (i in players.indices) {
      val player = players[i]
      val playerNumber = i + 1
      if (!swap) player.playerNumber = playerNumber
      player.timeouts = 0
      player.frameCount = 0
      actionQueueBuilder[i] =
        PlayerActionQueue(playerNumber, player, players.size, bufferSize, timeoutMillis)
      // SF MOD - player.setPlayerNumber(playerNumber);
      // SF MOD - Delay Value = [(60/connectionType) * (ping/1000)] + 1
      val delayVal = 60 / player.connectionType.byteValue * (player.ping.toDouble() / 1000) + 1
      player.frameDelay = delayVal.toInt()
      if (delayVal.toInt() > highestUserFrameDelay) {
        highestUserFrameDelay = delayVal.toInt()
      }
      if (ignoringUnnecessaryServerActivity) {
        player.ignoringUnnecessaryServerActivity = true
        announce("This game is ignoring ALL server activity during gameplay!", player)
      }
      /*else{
      	player.setP2P(false);
      }*/
      logger.atInfo().log("%s: %s is player number %d", this, player, playerNumber)
      autoFireDetector.addPlayer(player, playerNumber)
    }
    playerActionQueue = actionQueueBuilder.map { it!! }.toTypedArray()
    statsCollector?.markGameAsStarted(server, this)

    /*if(user.getConnectionType() > KailleraUser.CONNECTION_TYPE_GOOD || user.getConnectionType() < KailleraUser.CONNECTION_TYPE_GOOD){
    	//sameDelay = true;
    }*/

    // timeoutMillis = highestPing;
    addEvent(GameStartedEvent(this))
  }

  @Synchronized
  @Throws(UserReadyException::class)
  fun ready(user: KailleraUser, playerNumber: Int) {
    if (!players.contains(user)) {
      logger.atWarning().log("%s ready game failed: not in %s", user, this)
      throw UserReadyException(EmuLang.getString("KailleraGame.ReadyGameErrorNotInGame"))
    }
    if (status != GameStatus.SYNCHRONIZING) {
      logger.atWarning().log("%s ready failed: %s status is %s", user, this, status)
      throw UserReadyException(EmuLang.getString("KailleraGame.ReadyGameErrorIncorrectState"))
    }
    if (playerActionQueue == null) {
      logger.atSevere().log("%s ready failed: %s playerActionQueues == null!", user, this)
      throw UserReadyException(EmuLang.getString("KailleraGame.ReadyGameErrorInternalError"))
    }
    logger.atInfo().log("%s (player %d) is ready to play: %s", user, playerNumber, this)
    playerActionQueue!![playerNumber - 1].synched = true
    if (synchedCount == players.size) {
      logger.atInfo().log("%s all players are ready: starting...", this)
      status = GameStatus.PLAYING
      isSynched = true
      startTimeoutTime = System.currentTimeMillis()
      addEvent(AllReadyEvent(this))
      var frameDelay = (highestUserFrameDelay + 1) * owner.connectionType.byteValue - 1
      if (sameDelay) {
        announce("This game's delay is: $highestUserFrameDelay ($frameDelay frame delay)")
      } else {
        var i = 0
        while (i < playerActionQueue!!.size && i < players.size) {
          val player = players[i]
          // do not show delay if stealth mode
          if (player != null && !player.inStealthMode) {
            frameDelay = (player.frameDelay + 1) * player.connectionType.byteValue - 1
            announce("P${i + 1} Delay = ${player.frameDelay} ($frameDelay frame delay)")
          }
          i++
        }
      }
    }
  }

  @Synchronized
  @Throws(DropGameException::class)
  fun drop(user: KailleraUser, playerNumber: Int) {
    if (!players.contains(user)) {
      logger.atWarning().log("%s drop game failed: not in %s", user, this)
      throw DropGameException(EmuLang.getString("KailleraGame.DropGameErrorNotInGame"))
    }
    if (playerActionQueue == null) {
      logger.atSevere().log("%s drop failed: %s playerActionQueues == null!", user, this)
      throw DropGameException(EmuLang.getString("KailleraGame.DropGameErrorInternalError"))
    }
    logger.atInfo().log("%s dropped: %s", user, this)
    if (playerNumber - 1 < playerActionQueue!!.size)
      playerActionQueue!![playerNumber - 1].synched = false
    if (synchedCount < 2 && isSynched) {
      isSynched = false
      for (q in playerActionQueue!!) {
        q.synched = false
      }
      logger.atInfo().log("%s: game desynched: less than 2 players playing!", this)
    }
    autoFireDetector.stop(playerNumber)
    if (playingCount == 0) {
      if (startN != -1) {
        startN = -1
        announce("StartN is now off.")
      }
      status = GameStatus.WAITING
    }
    addEvent(UserDroppedGameEvent(this, user, playerNumber))
    if (user.ignoringUnnecessaryServerActivity) {
      // KailleraUser u = (KailleraUser) user;
      // u.addEvent(ACK.ServerACK.create(.getNextMessageNumber());
      // u.addEvent(new ConnectedEvent(server, user));
      // u.addEvent(new UserQuitEvent(server, user, "Rejoining..."));
      // try{user.quit("Rejoining...");}catch(Exception e){}
      announce("Rejoin server to update client of ignored server activity!", user)
    }
  }

  @Throws(DropGameException::class, QuitGameException::class, CloseGameException::class)
  fun quit(user: KailleraUser, playerNumber: Int) {
    synchronized(this) {
      if (!players.remove(user)) {
        logger.atWarning().log("%s quit game failed: not in %s", user, this)
        throw QuitGameException(EmuLang.getString("KailleraGame.QuitGameErrorNotInGame"))
      }
      logger.atInfo().log("%s quit: %s", user, this)
      addEvent(UserQuitGameEvent(this, user))
      user.ignoringUnnecessaryServerActivity = false
      swap = false
      if (status == GameStatus.WAITING) {
        for (i in players.indices) {
          val player = getPlayer(i + 1)
          player!!.playerNumber = i + 1
          logger.atFine().log("%s:::%d", player.userData.name, player.playerNumber)
        }
      }
    }
    if (user == owner) server.closeGame(this, user)
    else server.addEvent(GameStatusChangedEvent(server, this))
  }

  @Synchronized
  @Throws(CloseGameException::class)
  fun close(user: KailleraUser) {
    if (user != owner) {
      logger.atWarning().log("%s close game denied: not the owner of %s", user, this)
      throw CloseGameException(EmuLang.getString("KailleraGame.CloseGameErrorNotGameOwner"))
    }
    if (isSynched) {
      isSynched = false
      for (q in playerActionQueue!!) q.synched = false
      logger.atInfo().log("%s: game desynched: game closed!", this)
    }
    players.forEach {
      it.apply {
        status = UserStatus.IDLE
        isMuted = false
        ignoringUnnecessaryServerActivity = false
        game = null
      }
    }
    autoFireDetector.stop()
    players.clear()
  }

  @Synchronized
  fun droppedPacket(user: KailleraUser) {
    if (!isSynched) return
    val playerNumber = user.playerNumber
    if (user.playerNumber > playerActionQueue!!.size) {
      logger
        .atInfo()
        .log(
          "%s: %s: player desynched: dropped a packet! Also left the game already: KailleraGame -> DroppedPacket",
          this,
          user
        )
    }
    if (playerActionQueue != null && playerActionQueue!![playerNumber - 1].synched) {
      playerActionQueue!![playerNumber - 1].synched = false
      logger.atInfo().log("%s: %s: player desynched: dropped a packet!", this, user)
      addEvent(
        PlayerDesynchEvent(
          this,
          user,
          EmuLang.getString("KailleraGame.DesynchDetectedDroppedPacket", user.userData.name)
        )
      )
      if (synchedCount < 2 && isSynched) {
        isSynched = false
        for (q in playerActionQueue!!) q.synched = false
        logger.atInfo().log("%s: game desynched: less than 2 players synched!", this)
      }
    }
  }

  @Throws(GameDataException::class)
  fun addData(user: KailleraUser, playerNumber: Int, data: ByteArray) {
    val playerActionQueueCopy = playerActionQueue ?: return

    // int bytesPerAction = (data.length / actionsPerMessage);
    var timeoutCounter = 0
    // int arraySize = (playerActionQueues.length * actionsPerMessage * user.getBytesPerAction());
    if (!isSynched) {
      throw GameDataException(
        EmuLang.getString("KailleraGame.DesynchedWarning"),
        data,
        actionsPerMessage,
        playerNumber,
        playerActionQueueCopy.size
      )
    }
    playerActionQueueCopy[playerNumber - 1].addActions(data)
    autoFireDetector.addData(playerNumber, data, user.bytesPerAction)
    val response = ByteArray(user.arraySize)
    for (actionCounter in 0 until actionsPerMessage) {
      for (playerCounter in playerActionQueueCopy.indices) {
        while (isSynched) {
          try {
            playerActionQueueCopy[playerCounter].getAction(
              playerNumber,
              response,
              actionCounter * (playerActionQueueCopy.size * user.bytesPerAction) +
                playerCounter * user.bytesPerAction,
              user.bytesPerAction
            )
            break
          } catch (e: PlayerTimeoutException) {
            e.timeoutNumber = ++timeoutCounter
            handleTimeout(e)
          }
        }
      }
    }
    if (!isSynched)
      throw GameDataException(
        EmuLang.getString("KailleraGame.DesynchedWarning"),
        data,
        user.bytesPerAction,
        playerNumber,
        playerActionQueueCopy.size
      )
    user.addEvent(GameDataEvent(this, response))
  }

  // it's very important this method is synchronized
  @Synchronized
  private fun handleTimeout(e: PlayerTimeoutException) {
    if (!isSynched) return
    val playerNumber = e.playerNumber
    val timeoutNumber = e.timeoutNumber
    val playerActionQueue = playerActionQueue!![playerNumber - 1]
    if (!playerActionQueue.synched || e == playerActionQueue.lastTimeout) return
    playerActionQueue.lastTimeout = e
    val player: KailleraUser = e.player!!
    if (timeoutNumber < desynchTimeouts) {
      if (startTimeout) player.timeouts = player.timeouts + 1
      if (timeoutNumber % 12 == 0) {
        logger.atInfo().log("%s: %s: Timeout #%d", this, player, timeoutNumber / 12)
        addEvent(GameTimeoutEvent(this, player, timeoutNumber / 12))
      }
    } else {
      logger.atInfo().log("%s: %s: Timeout #%d", this, player, timeoutNumber / 12)
      playerActionQueue.synched = false
      logger.atInfo().log("%s: %s: player desynched: Lagged!", this, player)
      addEvent(
        PlayerDesynchEvent(
          this,
          player,
          EmuLang.getString("KailleraGame.DesynchDetectedPlayerLagged", player.userData.name)
        )
      )
      if (synchedCount < 2) {
        isSynched = false
        for (q in this.playerActionQueue!!) q.synched = false
        logger.atInfo().log("%s: game desynched: less than 2 players synched!", this)
      }
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
