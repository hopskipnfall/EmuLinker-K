package org.emulinker.kaillera.model.impl

import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Throws
import kotlin.time.Duration.Companion.seconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.UserStatus
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
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil.threadSleep

class KailleraGameImpl(
  override val id: Int,
  override val romName: String,
  override val owner: KailleraUser,
  override val server: KailleraServer,
  val bufferSize: Int,
) : KailleraGame {

  override var highestUserFrameDelay = 0
  override var maxPing = 1000
  override var startN = -1
  override var ignoringUnnecessaryServerActivity = false
  override var sameDelay = false
  override var startTimeout = false
  override var maxUsers = 8
    set(maxUsers) {
      field = maxUsers
      server.addEvent(GameStatusChangedEvent(server, this))
    }
  override var startTimeoutTime: Long = 0
    private set
  override val players: MutableList<KailleraUser> = CopyOnWriteArrayList()

  val mutedUsers: MutableList<String> = mutableListOf()
  var aEmulator = "any"
  var aConnection = "any"
  val startDate: Date = Date()
  @JvmField var swap = false

  override var status = GameStatus.WAITING
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

  override var playerActionQueue: Array<PlayerActionQueue>? = null
    private set

  override val clientType: String?
    get() = owner.clientType

  val autoFireDetector: AutoFireDetector?

  override fun getPlayerNumber(user: KailleraUser): Int {
    return players.indexOf(user) + 1
  }

  override fun getPlayer(playerNumber: Int): KailleraUser? {
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
    get() {
      if (playerActionQueue == null) return 0
      return playerActionQueue!!.count { it.synched }
    }

  private fun addEvent(event: GameEvent?) {
    for (player in players) player.addEvent(event)
  }

  @Synchronized
  @Throws(GameChatException::class)
  override fun chat(user: KailleraUser, message: String?) {
    if (!players.contains(user)) {
      logger.atWarning().log("%s game chat denied: not in %s", user, this)
      throw GameChatException(EmuLang.getString("KailleraGameImpl.GameChatErrorNotInGame"))
    }
    if (user.accessLevel == AccessManager.ACCESS_NORMAL) {
      if (server.maxGameChatLength > 0 && message!!.length > server.maxGameChatLength) {
        logger
          .atWarning()
          .log("%s gamechat denied: Message Length > %d", user, server.maxGameChatLength)
        addEvent(
          GameInfoEvent(
            this,
            EmuLang.getString("KailleraGameImpl.GameChatDeniedMessageTooLong"),
            user
          )
        )
        throw GameChatException(EmuLang.getString("KailleraGameImpl.GameChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s, %s gamechat: %s", user, this, message)
    addEvent(GameChatEvent(this, user, message!!))
  }

  @Synchronized
  fun announce(announcement: String, toUser: KailleraUser? = null) {
    addEvent(GameInfoEvent(this, announcement, toUser))
  }

  @Synchronized
  @Throws(GameKickException::class)
  override fun kick(requester: KailleraUser, userID: Int) {
    if (requester.accessLevel < AccessManager.ACCESS_ADMIN) {
      if (requester != owner) {
        logger.atWarning().log("%s kick denied: not the owner of %s", requester, this)
        throw GameKickException(EmuLang.getString("KailleraGameImpl.GameKickDeniedNotGameOwner"))
      }
    }
    if (requester.id == userID) {
      logger.atWarning().log("%s kick denied: attempt to kick self", requester)
      throw GameKickException(EmuLang.getString("KailleraGameImpl.GameKickDeniedCannotKickSelf"))
    }
    for (player in players) {
      if (player.id == userID) {
        try {
          if (requester.accessLevel != AccessManager.ACCESS_SUPERADMIN) {
            if (player.accessLevel >= AccessManager.ACCESS_ADMIN) {
              return
            }
          }
          logger.atInfo().log("%s kicked: %s from %s", requester, userID, this)
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
    logger.atWarning().log("%s kick failed: user %s not found in: %s", requester, userID, this)
    throw GameKickException(EmuLang.getString("KailleraGameImpl.GameKickErrorUserNotFound"))
  }

  @Synchronized
  @Throws(JoinGameException::class)
  override fun join(user: KailleraUser): Int {
    val access = server.accessManager.getAccess(user.socketAddress!!.address)

    // SF MOD - Join room spam protection
    if (lastAddress == user.connectSocketAddress.address.hostAddress) {
      lastAddressCount++
      if (lastAddressCount >= 4) {
        logger.atInfo().log("%s join spam protection: %d from %s", user, user.id, this)
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
      throw JoinGameException(EmuLang.getString("KailleraGameImpl.JoinGameErrorAlreadyInGame"))
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
        throw JoinGameException("Owner only allows connection type: " + owner.connectionType)
      }
    }
    if (
      access < AccessManager.ACCESS_ADMIN &&
        kickedUsers.contains(user.connectSocketAddress.address.hostAddress)
    ) {
      logger.atWarning().log("%s join game denied: previously kicked: %s", user, this)
      throw JoinGameException(EmuLang.getString("KailleraGameImpl.JoinGameDeniedPreviouslyKicked"))
    }
    if (access == AccessManager.ACCESS_NORMAL && status != GameStatus.WAITING) {
      logger
        .atWarning()
        .log("%s join game denied: attempt to join game in progress: %s", user, this)
      throw JoinGameException(EmuLang.getString("KailleraGameImpl.JoinGameDeniedGameIsInProgress"))
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
        threadSleep(1.seconds)
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
    		announce(EmuLang.getString("KailleraGameImpl.AutofireDetectionOn"));
    		announce(EmuLang.getString("KailleraGameImpl.AutofireCurrentSensitivity", autoFireDetector.getSensitivity()));
    	}
    	else
    	{
    		announce(EmuLang.getString("KailleraGameImpl.AutofireDetectionOff"));
    	}
    	announce(EmuLang.getString("KailleraGameImpl.GameHelp"));
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
          user.name + " using different emulator version: " + user.clientType,
          null
        )
      )
    return players.indexOf(user) + 1
  }

  @Synchronized
  @Throws(StartGameException::class)
  override fun start(user: KailleraUser) {
    val access = server.accessManager.getAccess(user.socketAddress!!.address)
    if (user != owner && access < AccessManager.ACCESS_ADMIN) {
      logger.atWarning().log("%s start game denied: not the owner of %s", user, this)
      throw StartGameException(
        EmuLang.getString("KailleraGameImpl.StartGameDeniedOnlyOwnerMayStart")
      )
    }
    if (status == GameStatus.SYNCHRONIZING) {
      logger.atWarning().log("%s start game failed: %s status is %s", user, this, status)
      throw StartGameException(EmuLang.getString("KailleraGameImpl.StartGameErrorSynchronizing"))
    } else if (status == GameStatus.PLAYING) {
      logger.atWarning().log("%s start game failed: %s status is %s", user, this, status)
      throw StartGameException(EmuLang.getString("KailleraGameImpl.StartGameErrorStatusIsPlaying"))
    }
    if (access == AccessManager.ACCESS_NORMAL && players.size < 2 && !server.allowSinglePlayer) {
      logger.atWarning().log("%s start game denied: %s needs at least 2 players", user, this)
      throw StartGameException(
        EmuLang.getString("KailleraGameImpl.StartGameDeniedSinglePlayerNotAllowed")
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
                "KailleraGameImpl.StartGameConnectionTypeMismatchInfo",
                owner.connectionType
              ),
              null
            )
          )
          throw StartGameException(
            EmuLang.getString("KailleraGameImpl.StartGameDeniedConnectionTypeMismatch")
          )
        }
        if (player.clientType != clientType) {
          logger
            .atWarning()
            .log("%s start game denied: %s: All players must use the same emulator!", user, this)
          addEvent(
            GameInfoEvent(
              this,
              EmuLang.getString("KailleraGameImpl.StartGameEmulatorMismatchInfo", clientType),
              null
            )
          )
          throw StartGameException(
            EmuLang.getString("KailleraGameImpl.StartGameDeniedEmulatorMismatch")
          )
        }
      }
    }
    logger.atInfo().log("%s started: %s", user, this)
    status = GameStatus.SYNCHRONIZING
    autoFireDetector?.start(players.size)
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
        PlayerActionQueue(playerNumber, player, players.size, bufferSize, timeoutMillis, true)
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
      logger.atInfo().log("%s: %s is player number %s", this, player, playerNumber)
      autoFireDetector?.addPlayer(player, playerNumber)
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
  override fun ready(user: KailleraUser?, playerNumber: Int) {
    if (!players.contains(user)) {
      logger.atWarning().log(user.toString() + " ready game failed: not in " + this)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorNotInGame"))
    }
    if (status != GameStatus.SYNCHRONIZING) {
      logger.atWarning().log("%s ready failed: %s status is %s", user, this, status)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorIncorrectState"))
    }
    if (playerActionQueue == null) {
      logger.atSevere().log("%s ready failed: %s playerActionQueues == null!", user, this)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorInternalError"))
    }
    logger.atInfo().log("%s (player %s) is ready to play: %s", user, playerNumber, this)
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
  override fun drop(user: KailleraUser, playerNumber: Int) {
    if (!players.contains(user)) {
      logger.atWarning().log("%s drop game failed: not in %s", user, this)
      throw DropGameException(EmuLang.getString("KailleraGameImpl.DropGameErrorNotInGame"))
    }
    if (playerActionQueue == null) {
      logger.atSevere().log("%s drop failed: %s playerActionQueues == null!", user, this)
      throw DropGameException(EmuLang.getString("KailleraGameImpl.DropGameErrorInternalError"))
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
    autoFireDetector?.stop(playerNumber)
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
      // u.addEvent(ServerACK.create(.getNextMessageNumber());
      // u.addEvent(new ConnectedEvent(server, user));
      // u.addEvent(new UserQuitEvent(server, user, "Rejoining..."));
      // try{user.quit("Rejoining...");}catch(Exception e){}
      announce("Rejoin server to update client of ignored server activity!", user)
    }
  }

  @Throws(DropGameException::class, QuitGameException::class, CloseGameException::class)
  override fun quit(user: KailleraUser, playerNumber: Int) {
    synchronized(this) {
      if (!players.remove(user)) {
        logger.atWarning().log("%s quit game failed: not in %s", user, this)
        throw QuitGameException(EmuLang.getString("KailleraGameImpl.QuitGameErrorNotInGame"))
      }
      logger.atInfo().log("%s quit: %s", user, this)
      addEvent(UserQuitGameEvent(this, user))
      user.ignoringUnnecessaryServerActivity = false
      swap = false
      if (status == GameStatus.WAITING) {
        for (i in players.indices) {
          getPlayer(i + 1)!!.playerNumber = i + 1
          logger.atFine().log(getPlayer(i + 1)!!.name + ":::" + getPlayer(i + 1)!!.playerNumber)
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
      throw CloseGameException(EmuLang.getString("KailleraGameImpl.CloseGameErrorNotGameOwner"))
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
    autoFireDetector?.stop()
    players.clear()
  }

  @Synchronized
  override fun droppedPacket(user: KailleraUser) {
    if (!isSynched) return
    val playerNumber = user.playerNumber
    if (user.playerNumber > playerActionQueue!!.size) {
      logger
        .atInfo()
        .log(
          "%s: %s: player desynched: dropped a packet! Also left the game already: KailleraGameImpl -> DroppedPacket",
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
          EmuLang.getString("KailleraGameImpl.DesynchDetectedDroppedPacket", user.name)
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
  override fun addData(user: KailleraUser, playerNumber: Int, data: ByteArray) {
    val playerActionQueueCopy = playerActionQueue ?: return

    // int bytesPerAction = (data.length / actionsPerMessage);
    var timeoutCounter = 0
    // int arraySize = (playerActionQueues.length * actionsPerMessage * user.getBytesPerAction());
    if (!isSynched) {
      throw GameDataException(
        EmuLang.getString("KailleraGameImpl.DesynchedWarning"),
        data,
        actionsPerMessage,
        playerNumber,
        playerActionQueueCopy.size
      )
    }
    playerActionQueueCopy[playerNumber - 1].addActions(data)
    autoFireDetector?.addData(playerNumber, data, user.bytesPerAction)
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
        EmuLang.getString("KailleraGameImpl.DesynchedWarning"),
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
          EmuLang.getString("KailleraGameImpl.DesynchDetectedPlayerLagged", player.name)
        )
      )
      if (synchedCount < 2) {
        isSynched = false
        for (q in this.playerActionQueue!!) q.synched = false
        logger.atInfo().log("%s: game desynched: less than 2 players synched!", this)
      }
    }
  }

  init {
    autoFireDetector = server.getAutoFireDetector(this)
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
