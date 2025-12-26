package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import com.google.protobuf.util.Timestamps
import java.io.FileOutputStream
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.event.AllReadyEvent
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameEvent
import org.emulinker.kaillera.model.event.GameInfoEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.GameStatusChangedEvent
import org.emulinker.kaillera.model.event.PlayerDesynchEvent
import org.emulinker.kaillera.model.event.UserDroppedGameEvent
import org.emulinker.kaillera.model.event.UserJoinedGameEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.DropGameException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.exception.GameKickException
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.exception.UserReadyException
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.emulinker.kaillera.model.impl.PlayerActionQueue
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.proto.EventKt.GameStartKt.playerDetails
import org.emulinker.proto.EventKt.LagstatSummaryKt.playerAttributedLag
import org.emulinker.proto.EventKt.fanOut
import org.emulinker.proto.EventKt.gameStart
import org.emulinker.proto.EventKt.lagstatSummary
import org.emulinker.proto.EventKt.receivedGameData
import org.emulinker.proto.GameLog
import org.emulinker.proto.Player
import org.emulinker.proto.Player.PLAYER_FOUR
import org.emulinker.proto.Player.PLAYER_ONE
import org.emulinker.proto.Player.PLAYER_THREE
import org.emulinker.proto.Player.PLAYER_TWO
import org.emulinker.proto.event
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil.threadSleep
import org.emulinker.util.EmuUtil.toMillisDouble
import org.emulinker.util.TimeOffsetCache
import org.emulinker.util.VariableSizeByteArray

class KailleraGame(
  val id: Int,
  val romName: String,
  val owner: KailleraUser,
  val server: KailleraServer,
  val bufferSize: Int,
  private val flags: RuntimeFlags,
  private val clock: Clock,
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

  /** The last time we logged lagstat to [gameLogBuilder]. */
  private var lastLagstatNs = 0L

  /**
   * Record of all game log events.
   *
   * This feature must be activated by an admin with the `/loggame` command.
   */
  var gameLogBuilder: GameLog.Builder? = null

  /**
   * Whether the game is holding data for the current frame, waiting on one or more players before
   * fanning out.
   */
  var waitingOnData = false
  /**
   * If `waitingOnPlayerNumber[playerNumber - 1]` is `true`, we are waiting on data for that player.
   */
  val waitingOnPlayerNumber = BooleanArray(10) { false }

  // TODO(nue): Combine this with [KailleraUser.singleFrameDurationNs].
  var singleFrameDurationForLagCalculationOnlyNs = 0L
    private set

  /** Last time we fanned out data for a frame. */
  private var lastFrameNs = System.nanoTime()

  var startTimeoutTime: Instant? = null
    private set

  val players: MutableList<KailleraUser> = CopyOnWriteArrayList()

  var lastLagReset = clock.now()
    private set

  private var lagLeewayNs = 0.seconds.inWholeNanoseconds
  var totalDriftNs = 0.seconds.inWholeNanoseconds
  val totalDriftCache = TimeOffsetCache(delay = flags.lagstatDuration, resolution = 5.seconds)

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

  private var lastAddress = "null"
  private var lastAddressCount = 0
  private var isSynched = false

  private val statsCollector: StatsCollector? = server.statsCollector
  private val kickedUsers: MutableList<String> = ArrayList()

  private val actionsPerMessage = owner.connectionType.byteValue.toInt()

  lateinit var playerActionQueues: Array<PlayerActionQueue>
    private set

  val clientType: String?
    get() = owner.clientType

  val autoFireDetector: AutoFireDetector = server.getAutoFireDetector(this)

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

  override fun toString() =
    "Game[id=$id name=${if (romName.length > 15) romName.take(15) + "..." else romName}]"

  private val playingCount: Int
    get() = players.asSequence().filter { it.status == UserStatus.PLAYING }.count()

  private val synchedCount: Int
    get() =
      if (this::playerActionQueues.isInitialized) playerActionQueues.count { it.synced } else 0

  private fun addEventForAllPlayers(event: GameEvent) {
    for (player in players) player.queueEvent(event)
  }

  @Throws(GameChatException::class)
  fun chat(user: KailleraUser, message: String) {
    if (user !in players) {
      logger.atWarning().log("%s game chat denied: not in %s", user, this)
      throw GameChatException(EmuLang.getString("KailleraGameImpl.GameChatErrorNotInGame"))
    }
    if (user.accessLevel == AccessManager.ACCESS_NORMAL) {
      if (server.maxGameChatLength > 0 && message.length > server.maxGameChatLength) {
        logger
          .atWarning()
          .log("%s gamechat denied: Message Length > %d", user, server.maxGameChatLength)
        addEventForAllPlayers(
          GameInfoEvent(
            this,
            EmuLang.getString("KailleraGameImpl.GameChatDeniedMessageTooLong"),
            user,
          )
        )
        throw GameChatException(EmuLang.getString("KailleraGameImpl.GameChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s, %s gamechat: %s", user, this, message)
    addEventForAllPlayers(GameChatEvent(this, user, message))
  }

  fun announce(announcement: String, toUser: KailleraUser? = null) {
    logger.atInfo().log("[ %s ] Announcement to %s: %s", this, toUser ?: "all", announcement)
    addEventForAllPlayers(GameInfoEvent(this, announcement, toUser))
  }

  @Synchronized
  @Throws(GameKickException::class)
  fun kick(requester: KailleraUser, userID: Int) {
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
  fun join(user: KailleraUser): Int {
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
    if (access < AccessManager.ACCESS_ELEVATED && user.ping > maxPing.milliseconds) {
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
            user.connectionType,
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
    addEventForAllPlayers(UserJoinedGameEvent(this, user))

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
      addEventForAllPlayers(
        GameInfoEvent(this, user.name + " using different emulator version: " + user.clientType)
      )
    return players.indexOf(user) + 1
  }

  @Synchronized
  @Throws(StartGameException::class)
  fun start(user: KailleraUser) {
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
    if (access == AccessManager.ACCESS_NORMAL && players.size < 2 && !flags.allowSinglePlayer) {
      logger.atWarning().log("%s start game denied: %s needs at least 2 players", user, this)
      throw StartGameException(
        EmuLang.getString("KailleraGameImpl.StartGameDeniedSinglePlayerNotAllowed")
      )
    }

    singleFrameDurationForLagCalculationOnlyNs =
      (1.seconds / user.connectionType.getUpdatesPerSecond(GAME_FPS)).inWholeNanoseconds

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
              this,
            )
          addEventForAllPlayers(
            GameInfoEvent(
              this,
              EmuLang.getString(
                "KailleraGameImpl.StartGameConnectionTypeMismatchInfo",
                owner.connectionType,
              ),
              null,
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
          addEventForAllPlayers(
            GameInfoEvent(
              this,
              EmuLang.getString("KailleraGameImpl.StartGameEmulatorMismatchInfo", clientType),
              null,
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
    autoFireDetector.start(players.size)
    val actionQueueBuilder = mutableListOf<PlayerActionQueue>()
    startTimeout = false
    highestUserFrameDelay = 1
    if (server.usersMap.values.size > 60) {
      ignoringUnnecessaryServerActivity = true
    }
    for ((i, player) in players.withIndex()) {
      val playerNumber = i + 1
      if (!swap) player.playerNumber = playerNumber
      player.frameCount = 0
      actionQueueBuilder.add(
        PlayerActionQueue(
          playerNumber = playerNumber,
          player,
          numPlayers = players.size,
          gameBufferSize = bufferSize,
        )
      )
      val delayVal =
        GAME_FPS.toDouble() / player.connectionType.byteValue *
          (player.ping.toMillisDouble() / 1000.0) + 1.0
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
      logger.atFine().log("%s: %s is player number %s", this, player, playerNumber)
      autoFireDetector.addPlayer(player, playerNumber)
    }
    playerActionQueues = actionQueueBuilder.toTypedArray()
    statsCollector?.markGameAsStarted(server, this)
    gameLogBuilder?.addEvents(
      event {
        timestampNs = System.nanoTime()
        gameStart = gameStart {
          timestamp = Timestamps.now()
          for (player in this@KailleraGame.players) {
            this@gameStart.players += playerDetails {
              playerNumber = player.playerNumber.toPlayerNumberProto()

              frameDelay = player.frameDelay
              pingMs = player.ping.toMillisDouble()
            }
          }
        }
      }
    )

    /*if(user.getConnectionType() > KailleraUser.CONNECTION_TYPE_GOOD || user.getConnectionType() < KailleraUser.CONNECTION_TYPE_GOOD){
    	//sameDelay = true;
    }*/

    // timeoutMillis = highestPing;
    addEventForAllPlayers(GameStartedEvent(this))
  }

  @Synchronized
  @Throws(UserReadyException::class)
  fun ready(user: KailleraUser, playerNumber: Int) {
    if (user !in players) {
      logger.atWarning().log("%s ready game failed: not in %s", user, this)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorNotInGame"))
    }
    if (status != GameStatus.SYNCHRONIZING) {
      logger.atWarning().log("%s ready failed: %s status is %s", user, this, status)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorIncorrectState"))
    }
    if (!this::playerActionQueues.isInitialized) {
      logger
        .atSevere()
        .log("%s ready failed: %s playerActionQueues is not initialized!", user, this)
      throw UserReadyException(EmuLang.getString("KailleraGameImpl.ReadyGameErrorInternalError"))
    }
    logger.atFine().log("%s (player %s) is ready to play: %s", user, playerNumber, this)
    playerActionQueues[playerNumber - 1].markSynced()
    if (synchedCount == players.size) {
      logger.atFine().log("%s all players are ready: starting...", this)
      status = GameStatus.PLAYING
      isSynched = true
      startTimeoutTime = clock.now()
      addEventForAllPlayers(AllReadyEvent(this))
      var frameDelay = (highestUserFrameDelay + 1) * owner.connectionType.byteValue - 1
      if (sameDelay) {
        announce("This game's delay is: $highestUserFrameDelay ($frameDelay frame delay)")
      } else {
        var i = 0
        while (i < playerActionQueues.size && i < players.size) {
          val player = players[i]
          // do not show delay if stealth mode
          if (!player.inStealthMode) {
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
    if (user !in players) {
      logger.atWarning().log("%s drop game failed: not in %s", user, this)
      throw DropGameException(EmuLang.getString("KailleraGameImpl.DropGameErrorNotInGame"))
    }
    if (!this::playerActionQueues.isInitialized) {
      logger.atSevere().log("%s drop failed: %s playerActionQueues is not initialized!", user, this)
      throw DropGameException(EmuLang.getString("KailleraGameImpl.DropGameErrorInternalError"))
    }
    logger.atInfo().log("%s dropped: %s", user, this)
    if (playerNumber - 1 < playerActionQueues.size) {
      playerActionQueues[playerNumber - 1].markDesynced()
    }
    autoFireDetector.stop(playerNumber)
    if (playingCount == 0) {
      if (startN != -1) {
        startN = -1
        announce("StartN is now off.")
      }
      status = GameStatus.WAITING
    }
    addEventForAllPlayers(UserDroppedGameEvent(this, user, playerNumber))
    if (user.ignoringUnnecessaryServerActivity) {
      // KailleraUser u = (KailleraUser) user;
      // u.addEvent(ServerACK.create(.getNextMessageNumber());
      // u.addEvent(new ConnectedEvent(server, user));
      // u.addEvent(new UserQuitEvent(server, user, "Rejoining..."));
      // try{user.quit("Rejoining...");}catch(Exception e){}
      announce("Rejoin server to update client of ignored server activity!", user)
    }
    if (waitingOnData) {
      maybeSendData(user)
    }
  }

  @Synchronized
  @Throws(DropGameException::class, QuitGameException::class, CloseGameException::class)
  fun quit(user: KailleraUser, playerNumber: Int) {
    if (!players.remove(user)) {
      logger.atWarning().log("%s quit game failed: not in %s", user, this)
      throw QuitGameException(EmuLang.getString("KailleraGameImpl.QuitGameErrorNotInGame"))
    }
    logger.atInfo().log("%s quit: %s", user, this)
    addEventForAllPlayers(UserQuitGameEvent(this, user))
    user.ignoringUnnecessaryServerActivity = false
    swap = false
    if (status == GameStatus.WAITING) {
      for (i in players.indices) {
        getPlayer(i + 1)!!.playerNumber = i + 1
        logger.atFine().log(getPlayer(i + 1)!!.name + ":::" + getPlayer(i + 1)!!.playerNumber)
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
      for (q in playerActionQueues) q.markDesynced()

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

    // Write the game log out to a compressed file.
    val gameLog = gameLogBuilder?.build()
    if (gameLog != null) {
      try {
        val filePath = createTempDirectory("gamelog").resolve("gamelog.bin.gz").toFile()
        FileOutputStream(filePath).use { fos ->
          GZIPOutputStream(fos).use { gzipOut ->
            gzipOut.write(gameLog.toByteArray())
            logger.atInfo().log("Stored game log for game %d at %s", id, filePath)
          }
        }
      } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Failed to save game log for game %d", id)
      }
    }
  }

  @Synchronized
  fun droppedPacket(user: KailleraUser) {
    if (!isSynched) return
    val playerNumber = user.playerNumber
    if (user.playerNumber > playerActionQueues.size) {
      logger
        .atInfo()
        .log(
          "%s: %s: player desynched: dropped a packet! Also left the game already: KailleraGameImpl -> DroppedPacket",
          this,
          user,
        )
    }
    if (playerActionQueues[playerNumber - 1].synced) {
      playerActionQueues[playerNumber - 1].markDesynced()
      logger.atInfo().log("%s: %s: player desynched: dropped a packet!", this, user)
      addEventForAllPlayers(
        PlayerDesynchEvent(
          this,
          user,
          EmuLang.getString("KailleraGameImpl.DesynchDetectedDroppedPacket", user.name),
        )
      )
      if (synchedCount < 2 && isSynched) {
        isSynched = false
        for (q in playerActionQueues) q.markDesynced()
        logger.atInfo().log("%s: game desynched: less than 2 players synched!", this)
      }
    }
  }

  /**
   * Adds data and suspends until all data is available, at which time it returns the sends new data
   * back to the client.
   */
  fun addData(user: KailleraUser, playerNumber: Int, data: VariableSizeByteArray): AddDataResult {
    if (!isSynched) return AddDataResult.IgnoringDesynched

    gameLogBuilder?.addEvents(
      event {
        timestampNs = checkNotNull(user.receivedGameDataNs) { "user.receivedGameDataNs is null!" }
        receivedGameData =
          when (user.playerNumber) {
            1 -> RECEIVED_FROM_P1
            2 -> RECEIVED_FROM_P2
            3 -> RECEIVED_FROM_P3
            4 -> RECEIVED_FROM_P4
            else -> throw IllegalStateException("Player number is out of bounds!")
          }
      }
    )

    // Add the data for the user to their own player queue.
    playerActionQueues[playerNumber - 1].addActions(data)

    autoFireDetector.addData(playerNumber, data, user.bytesPerAction)

    return maybeSendData(user)
  }

  /**
   * Checks if any user has a full set of new data to send.
   *
   * @param user The user who initiated this call.
   */
  // TODO(nue): This is probably not threadsafe but is being called from multiple threads.
  fun maybeSendData(user: KailleraUser): AddDataResult {
    // TODO(nue): This works for 2P but what about more? This probably results in unnecessary
    // messages.
    for (player in players) {
      val playerNumber = player.playerNumber

      // If there is new information about all synced players.
      if (
        playerActionQueues.all {
          !it.synced ||
            it.containsNewDataForPlayer(
              playerIndex = playerNumber - 1,
              actionLength = actionsPerMessage * user.bytesPerAction,
            )
        }
      ) {
        waitingOnData = false
        val joinedGameData =
          if (CompiledFlags.USE_CIRCULAR_BYTE_ARRAY_BUFFER) {
            player.circularVariableSizeByteArrayBuffer.borrow()
          } else {
            VariableSizeByteArray()
          }
        joinedGameData.size = user.arraySize
        for (actionCounter in 0 until actionsPerMessage) {
          for (playerActionQueueIndex in playerActionQueues.indices) {
            playerActionQueues[playerActionQueueIndex].getActionAndWriteToArray(
              readingPlayerIndex = playerNumber - 1,
              writeTo = joinedGameData,
              writeAtIndex =
                actionCounter * (playerActionQueues.size * user.bytesPerAction) +
                  playerActionQueueIndex * user.bytesPerAction,
              actionLength = user.bytesPerAction,
            )
          }
        }
        if (!isSynched) return AddDataResult.IgnoringDesynched
        player.doEvent(GameDataEvent(this, joinedGameData))
        player.updateUserDrift()
        val firstPlayer = players.firstOrNull()
        if (firstPlayer != null && firstPlayer.id == player.id) {
          updateGameDrift()
        }
      } else {
        waitingOnData = true
        playerActionQueues.forEach {
          waitingOnPlayerNumber[it.playerNumber - 1] =
            it.synced &&
              !it.containsNewDataForPlayer(
                playerIndex = playerNumber - 1,
                actionLength = actionsPerMessage * user.bytesPerAction,
              )
        }
      }
    }
    return AddDataResult.Success
  }

  fun resetLag() {
    totalDriftCache.clear()
    totalDriftNs = 0
    lastLagReset = clock.now()
  }

  /** Sets the game framerate for lag measuring purposes. */
  fun setGameFps(fps: Double) {
    singleFrameDurationForLagCalculationOnlyNs =
      (1.seconds / players.first().connectionType.getUpdatesPerSecond(fps)).inWholeNanoseconds
    resetLag()
    for (player in players) {
      player.resetLag()
    }
  }

  /** The total duration that the game has drifted over the lagstat measurement window. */
  val currentGameLag
    get() = (totalDriftNs - (totalDriftCache.getDelayedValue() ?: 0)).nanoseconds.absoluteValue

  private fun updateGameDrift() {
    val nowNs = System.nanoTime()

    val glb = gameLogBuilder
    if (glb != null) {
      glb.addEvents(
        event {
          timestampNs = nowNs
          fanOut = FAN_OUT
        }
      )

      // Log the /lagstat data once every 1 minute.
      if ((nowNs - lastLagstatNs).nanoseconds > 1.minutes) {
        glb.addEvents(
          event {
            timestampNs = nowNs
            lagstatSummary = lagstatSummary {
              windowDurationMs = flags.lagstatDuration.inWholeMilliseconds.toInt()
              gameLagMs = currentGameLag.toMillisDouble()

              for (p in players) {
                playerAttributedLags += playerAttributedLag {
                  player = p.playerNumber.toPlayerNumberProto()
                  attributedLagMs = p.lagAttributedToUser().toMillisDouble()
                }
              }
            }
          }
        )
        lastLagstatNs = nowNs
      }
    }

    val delaySinceLastResponseNs = nowNs - lastFrameNs

    lagLeewayNs += singleFrameDurationForLagCalculationOnlyNs - delaySinceLastResponseNs
    if (lagLeewayNs < 0) {
      // Lag leeway fell below zero. Lag occurred!
      totalDriftNs += lagLeewayNs
      lagLeewayNs = 0
    } else if (lagLeewayNs > singleFrameDurationForLagCalculationOnlyNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeewayNs = singleFrameDurationForLagCalculationOnlyNs
    }
    totalDriftCache.update(totalDriftNs, nowNs = nowNs)
    lastFrameNs = nowNs
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    /** Unfortunately Kaillera is built on the assumption that all games run at 60FPS. */
    const val GAME_FPS = 60

    // A few logging constants to avoid creating unnecessary objects.
    private val FAN_OUT = fanOut {}
    private val RECEIVED_FROM_P1 = receivedGameData { receivedFrom = PLAYER_ONE }
    private val RECEIVED_FROM_P2 = receivedGameData { receivedFrom = PLAYER_TWO }
    private val RECEIVED_FROM_P3 = receivedGameData { receivedFrom = PLAYER_THREE }
    private val RECEIVED_FROM_P4 = receivedGameData { receivedFrom = PLAYER_FOUR }

    private fun Int.toPlayerNumberProto(): Player =
      when (this) {
        1 -> PLAYER_ONE
        2 -> PLAYER_TWO
        3 -> PLAYER_THREE
        4 -> PLAYER_FOUR
        else -> throw IllegalStateException("Player number is out of bounds!")
      }
  }
}
