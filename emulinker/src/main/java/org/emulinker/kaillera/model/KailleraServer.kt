package org.emulinker.kaillera.model

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.util.Locale
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.lookingforgame.LookingForGameEvent
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.event.ChatEvent
import org.emulinker.kaillera.model.event.ConnectedEvent
import org.emulinker.kaillera.model.event.GameClosedEvent
import org.emulinker.kaillera.model.event.GameCreatedEvent
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameStatusChangedEvent
import org.emulinker.kaillera.model.event.InfoMessageEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.KailleraEventListener
import org.emulinker.kaillera.model.event.UserJoinedEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.exception.ChatException
import org.emulinker.kaillera.model.exception.ClientAddressException
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.CreateGameException
import org.emulinker.kaillera.model.exception.DropGameException
import org.emulinker.kaillera.model.exception.FloodException
import org.emulinker.kaillera.model.exception.LoginException
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.PingTimeException
import org.emulinker.kaillera.model.exception.QuitException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.kaillera.model.exception.UserNameException
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory
import org.emulinker.kaillera.model.impl.Trivia
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.CustomUserStrings
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.threadSleep
import org.emulinker.util.TaskScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/** Holds server-wide state. */
class KailleraServer(
  val accessManager: AccessManager,
  private val flags: RuntimeFlags,
  statsCollector: StatsCollector?,
  val releaseInfo: ReleaseInfo,
  private val autoFireDetectorFactory: AutoFireDetectorFactory,
  private val lookingForGameReporter: TwitterBroadcaster,
  metrics: MetricRegistry,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock,
) : KoinComponent {

  private val eventQueue = LinkedBlockingQueue<Pair<KailleraUser, KailleraEvent>>()

  fun queueEvent(user: KailleraUser, event: KailleraEvent) {
    eventQueue.offer(user to event)
  }

  private val userActionsExecutor: ThreadPoolExecutor by
    inject(qualifier = named("userActionsExecutor"))

  private var allowedConnectionTypes = BooleanArray(7)
  private val loginMessages: List<String> = buildList {
    var i = 1
    while (CustomUserStrings.hasString("KailleraServerImpl.LoginMessage.$i")) {
      add(CustomUserStrings.getString("KailleraServerImpl.LoginMessage.$i"))
      i++
    }
  }
  private var connectionCounter = 1
  private var gameCounter = 1

  var statsCollector: StatsCollector? = null

  val usersMap: MutableMap<Int, KailleraUser> = ConcurrentHashMap(flags.maxUsers)

  var gamesMap: MutableMap<Int, KailleraGame> = ConcurrentHashMap(flags.maxGames)

  var trivia: Trivia? = null

  var triviaThread: Thread? = null

  var switchTrivia = false

  fun getUser(userID: Int): KailleraUser? {
    return usersMap[userID]
  }

  fun getGame(gameID: Int): KailleraGame? {
    return gamesMap[gameID]
  }

  private val maxUserNameLength: Int = flags.maxUserNameLength
  val maxGameChatLength = flags.maxGameChatLength
  private val maxClientNameLength: Int = flags.maxClientNameLength

  private var timerTask: TimerTask? = null

  override fun toString(): String {
    return String.format(
      "KailleraServer[numUsers=%d numGames=%d]",
      usersMap.values.size,
      gamesMap.values.size,
    )
  }

  @Synchronized
  fun start() {
    timerTask =
      taskScheduler.scheduleRepeating(
        period = 1.minutes,
        initialDelay = 1.minutes,
        taskName = "run()",
      ) {
        run()
      }
  }

  @Synchronized
  fun stop() {
    stopFlag.set(true)
    usersMap.clear()
    gamesMap.clear()
    timerTask?.cancel()
    timerTask = null
  }

  // not synchronized because I know the caller will be thread safe
  private fun getNextUserID(): Int {
    if (connectionCounter > 0xFFFF) connectionCounter = 1
    return connectionCounter++
  }

  // not synchronized because I know the caller will be thread safe
  private fun getNextGameID(): Int {
    if (gameCounter > 0xFFFF) gameCounter = 1
    return gameCounter++
  }

  fun getAutoFireDetector(game: KailleraGame): AutoFireDetector =
    autoFireDetectorFactory.getInstance(game, flags.gameAutoFireSensitivity)

  @Synchronized
  @Throws(ServerFullException::class, NewConnectionException::class)
  fun newConnection(
    clientSocketAddress: InetSocketAddress,
    protocol: String,
    listener: KailleraEventListener,
  ): KailleraUser {
    // we'll assume at this point that ConnectController has already asked AccessManager if this IP
    // is banned, so no need to do it again here
    logger
      .atFine()
      .log(
        "Processing connection request from %s",
        EmuUtil.formatSocketAddress(clientSocketAddress),
      )
    val access = accessManager.getAccess(clientSocketAddress.address)

    // admins will be allowed in even if the server is full
    if (
      flags.maxUsers > 0 && usersMap.size >= flags.maxUsers && access <= AccessManager.ACCESS_NORMAL
    ) {
      logger
        .atWarning()
        .log(
          "Connection from %s denied: Server is full!",
          EmuUtil.formatSocketAddress(clientSocketAddress),
        )
      throw ServerFullException(EmuLang.getString("KailleraServerImpl.LoginDeniedServerFull"))
    }
    val userID = getNextUserID()
    val user = KailleraUser(userID, protocol, clientSocketAddress, listener, this, flags, clock)
    user.status = UserStatus.CONNECTING
    logger
      .atFine()
      .log(
        "%s attempting new connection using protocol %s from %s",
        user,
        protocol,
        EmuUtil.formatSocketAddress(clientSocketAddress),
      )
    usersMap[userID] = user
    return user
  }

  // TODO(nue): Could this withLock be the source of lag on server join?
  fun login(user: KailleraUser): Result<Unit> = withLock {
    logger
      .atInfo()
      .log(
        "%s: login request: clientAddress=%s, name=%s, ping=%s, client=%s, connection=%s",
        user,
        EmuUtil.formatSocketAddress(user.socketAddress!!),
        user.name,
        user.ping.toString(DurationUnit.MILLISECONDS, decimals = 1),
        user.clientType,
        user.connectionType,
      )
    if (user.loggedIn) {
      logger.atWarning().log("%s login denied: Already logged in!", user)
      return Result.failure(
        LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedAlreadyLoggedIn"))
      )
    }
    val userListKey = user.id
    val u: KailleraUser? = usersMap[userListKey]
    if (u == null) {
      logger.atWarning().log("%s login denied: Connection timed out!", user)
      return Result.failure(
        LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedConnectionTimedOut"))
      )
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (access < AccessManager.ACCESS_NORMAL) {
      logger.atInfo().log("%s login denied: Access denied", user)
      usersMap.remove(userListKey)
      return Result.failure(
        LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedAccessDenied"))
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.maxPing > 0.milliseconds &&
        user.ping > flags.maxPing
    ) {
      logger.atInfo().log("%s login denied: Ping %d ms > %s", user, user.ping, flags.maxPing)
      usersMap.remove(userListKey)
      return Result.failure(
        PingTimeException(
          EmuLang.getString(
            "KailleraServerImpl.LoginDeniedPingTooHigh",
            "${user.ping} > ${flags.maxPing}",
          )
        )
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        !allowedConnectionTypes[user.connectionType.byteValue.toInt()]
    ) {
      logger.atInfo().log("%s login denied: Connection %s Not Allowed", user, user.connectionType)
      usersMap.remove(userListKey)
      return Result.failure(
        LoginException(
          EmuLang.getString(
            "KailleraServerImpl.LoginDeniedConnectionTypeDenied",
            user.connectionType,
          )
        )
      )
    }
    if (user.ping < 0.milliseconds) {
      logger.atWarning().log("%s login denied: Invalid ping: %d", user, user.ping)
      usersMap.remove(userListKey)
      return Result.failure(
        PingTimeException(EmuLang.getString("KailleraServerImpl.LoginErrorInvalidPing", user.ping))
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL && user.name.isNullOrEmpty() || user.name!!.isBlank()
    ) {
      logger.atInfo().log("%s login denied: Empty UserName", user)
      usersMap.remove(userListKey)
      return Result.failure(
        UserNameException(EmuLang.getString("KailleraServerImpl.LoginDeniedUserNameEmpty"))
      )
    }

    // new SF MOD - Username filter
    val nameLower = user.name!!.lowercase(Locale.getDefault())
    if (
      user.name == "Server" ||
        nameLower.contains("|") ||
        (access == AccessManager.ACCESS_NORMAL &&
          (nameLower.contains("www.") ||
            nameLower.contains("http://") ||
            nameLower.contains("https://") ||
            nameLower.contains("\\") ||
            nameLower.contains("�") ||
            nameLower.contains("�")))
    ) {
      logger.atInfo().log("%s login denied: Illegal characters in UserName", user)
      usersMap.remove(userListKey)
      return Result.failure(
        UserNameException(
          EmuLang.getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
        )
      )
    }

    // access == AccessManager.ACCESS_NORMAL &&
    if (flags.maxUserNameLength > 0 && user.name!!.length > maxUserNameLength) {
      logger.atInfo().log("%s login denied: UserName Length > %d", user, maxUserNameLength)
      usersMap.remove(userListKey)
      return Result.failure(
        UserNameException(EmuLang.getString("KailleraServerImpl.LoginDeniedUserNameTooLong"))
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.maxClientNameLength > 0 &&
        user.clientType!!.length > maxClientNameLength
    ) {
      logger.atInfo().log("%s login denied: Client Name Length > %d", user, maxClientNameLength)
      usersMap.remove(userListKey)
      return Result.failure(
        UserNameException(EmuLang.getString("KailleraServerImpl.LoginDeniedEmulatorNameTooLong"))
      )
    }
    if (user.clientType!!.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s login denied: Illegal characters in EmulatorName", user)
      usersMap.remove(userListKey)
      return Result.failure(UserNameException("Illegal characters in Emulator Name"))
    }
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = user.name!!.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atInfo().log("%s login denied: Illegal characters in UserName", user)
          usersMap.remove(userListKey)
          return Result.failure(
            UserNameException(
              EmuLang.getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
            )
          )
        }
      }
    }
    if (u.status != UserStatus.CONNECTING) {
      usersMap.remove(userListKey)
      logger.atWarning().log("%s login denied: Invalid status=%s", user, u.status)
      return Result.failure(
        LoginException(EmuLang.getString("KailleraServerImpl.LoginErrorInvalidStatus", u.status))
      )
    }
    if (u.connectSocketAddress.address != user.socketAddress!!.address) {
      usersMap.remove(userListKey)
      logger
        .atWarning()
        .log(
          "%s login denied: Connect address does not match login address: %s != %s",
          user,
          u.connectSocketAddress.address.hostAddress,
          user.socketAddress!!.address.hostAddress,
        )
      return Result.failure(
        ClientAddressException(EmuLang.getString("KailleraServerImpl.LoginDeniedAddressMatchError"))
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL && !accessManager.isEmulatorAllowed(user.clientType!!)
    ) {
      logger
        .atInfo()
        .log("%s login denied: AccessManager denied emulator: %s", user, user.clientType)
      usersMap.remove(userListKey)
      return Result.failure(
        LoginException(
          EmuLang.getString("KailleraServerImpl.LoginDeniedEmulatorRestricted", user.clientType)
        )
      )
    }
    for (u2 in usersMap.values) {
      if (u2.loggedIn) {
        if (
          u2.id != u.id &&
            (u.connectSocketAddress.address == u2.connectSocketAddress.address) &&
            u.name == u2.name
        ) {
          // user is attempting to login more than once with the same name and address
          // logoff the old user and login the new one
          try {
            quit(u2, EmuLang.getString("KailleraServerImpl.ForcedQuitReconnected"))
          } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error forcing %s quit for reconnect!", u2)
          }
        } else if (
          u2.id != u.id &&
            u2.name!!.lowercase(Locale.getDefault()).trim { it <= ' ' } ==
              u.name!!.lowercase(Locale.getDefault()).trim { it <= ' ' }
        ) {
          usersMap.remove(userListKey)
          logger
            .atWarning()
            .log("%s login denied: Duplicating Names is not allowed! %s", user, u2.name)
          return Result.failure(
            ClientAddressException("Duplicating names is not allowed: " + u2.name)
          )
        }
        if (
          access == AccessManager.ACCESS_NORMAL &&
            u2.id != u.id &&
            (u.connectSocketAddress.address == u2.connectSocketAddress.address) &&
            u.name != u2.name &&
            !flags.allowMultipleConnections
        ) {
          usersMap.remove(userListKey)
          logger.atWarning().log("%s login denied: Address already logged in as %s", user, u2.name)
          return Result.failure(
            ClientAddressException(
              EmuLang.getString("KailleraServerImpl.LoginDeniedAlreadyLoggedInAs", u2.name)
            )
          )
        }
      }
    }

    // passed all checks
    user.accessLevel = access
    user.status = UserStatus.IDLE
    user.loggedIn = true
    usersMap[userListKey] = user
    user.queueEvent(ConnectedEvent(this, user))
    threadSleep(20.milliseconds)
    for (loginMessage in loginMessages) {
      user.queueEvent(InfoMessageEvent(user, loginMessage))
      threadSleep(20.milliseconds)
    }
    user.queueEvent(
      InfoMessageEvent(
        user,
        "${releaseInfo.productName} v${releaseInfo.version}: ${releaseInfo.websiteString}",
      )
    )
    if (CompiledFlags.DEBUG_BUILD) {
      user.queueEvent(
        InfoMessageEvent(
          user,
          "WARNING: This is an unoptimized debug build that should not be used in production.",
        )
      )
    }
    threadSleep(20.milliseconds)
    if (access > AccessManager.ACCESS_NORMAL) {
      logger
        .atInfo()
        .log("%s logged in successfully with %s access!", user, AccessManager.ACCESS_NAMES[access])
    } else {
      logger.atFine().log("%s logged in successfully", user)
    }

    // this is fairly ugly
    if (user.isEsfAdminClient) {
      user.queueEvent(InfoMessageEvent(user, ":ACCESS=" + user.accessStr))
      if (access >= AccessManager.ACCESS_SUPERADMIN) {
        var sb = StringBuilder()
        sb.append(":USERINFO=")
        var sbCount = 0
        for (u3 in usersMap.values) {
          if (!u3.loggedIn) continue
          sb.append(u3.id)
          sb.append(0x02.toChar())
          sb.append(u3.connectSocketAddress.address.hostAddress)
          sb.append(0x02.toChar())
          sb.append(u3.accessStr)
          sb.append(0x02.toChar())
          // str = u3.getName().replace(',','.');
          // str = str.replace(';','.');
          sb.append(u3.name)
          sb.append(0x02.toChar())
          sb.append(u3.ping)
          sb.append(0x02.toChar())
          sb.append(u3.status)
          sb.append(0x02.toChar())
          sb.append(u3.connectionType.byteValue.toInt())
          sb.append(0x03.toChar())
          sbCount++
          if (sb.length > 300) {
            user.queueEvent(InfoMessageEvent(user, sb.toString()))
            sb = StringBuilder()
            sb.append(":USERINFO=")
            sbCount = 0
            threadSleep(100.milliseconds)
          }
        }
        if (sbCount > 0) user.queueEvent(InfoMessageEvent(user, sb.toString()))
        threadSleep(100.milliseconds)
      }
    }
    threadSleep(20.milliseconds)
    if (access >= AccessManager.ACCESS_ADMIN) {
      user.queueEvent(
        InfoMessageEvent(user, EmuLang.getString("KailleraServerImpl.AdminWelcomeMessage"))
      )
      // Display messages to admins if they exist.
      for (message in AppModule.messagesToAdmins) {
        threadSleep(20.milliseconds)
        user.queueEvent(InfoMessageEvent(user, message))
      }
    }
    addEvent(UserJoinedEvent(this, user))
    threadSleep(20.milliseconds)
    val announcement = accessManager.getAnnouncement(user.socketAddress!!.address)
    if (announcement != null) announce(announcement, false)

    return Result.success(Unit)
  }

  @Throws(
    QuitException::class,
    DropGameException::class,
    QuitGameException::class,
    CloseGameException::class,
  )
  fun quit(user: KailleraUser, message: String) = withLock {
    lookingForGameReporter.cancelActionsForUser(user.id)
    if (!user.loggedIn) {
      usersMap.remove(user.id)
      logger.atSevere().log("%s quit failed: Not logged in", user)
      throw QuitException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (usersMap.remove(user.id) == null)
      logger.atSevere().log("%s quit failed: not in user list", user)
    val userGame = user.game
    if (userGame != null) user.quitGame()
    var quitMsg = message.trim { it <= ' ' }
    if (
      quitMsg.isBlank() ||
        (flags.maxQuitMessageLength > 0 && quitMsg.length > flags.maxQuitMessageLength)
    ) {
      quitMsg = EmuLang.getString("KailleraServerImpl.StandardQuitMessage")
    }
    logger.atInfo().log("%s quit: %s", user, quitMsg)
    val quitEvent = UserQuitEvent(this, user, quitMsg)
    addEvent(quitEvent)
    user.queueEvent(quitEvent)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  fun chat(to: KailleraUser, message: String) {
    var message = message
    if (!to.loggedIn) {
      logger.atSevere().log("%s chat failed: Not logged in", to)
      throw ChatException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    val access = accessManager.getAccess(to.socketAddress!!.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(to.socketAddress!!.address)
    ) {
      logger.atWarning().log("%s chat denied: Silenced: %s", to, message)
      throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedSilenced"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.chatFloodTime > Duration.ZERO &&
        clock.now() - to.lastChatTime < flags.chatFloodTime
    ) {
      logger.atWarning().log("%s chat denied: Flood: %s", to, message)
      throw FloodException(EmuLang.getString("KailleraServerImpl.ChatDeniedFloodControl"))
    }
    if (message == ":USER_COMMAND") {
      return
    }
    message = message.trim { it <= ' ' }
    if (message.isBlank() || message.startsWith("�") || message.startsWith("�")) return
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = message.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atWarning().log("%s chat denied: Illegal characters in message", to)
          throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedIllegalCharacters"))
        }
      }
      if (flags.maxChatLength > 0 && message.length > flags.maxChatLength) {
        logger.atWarning().log("%s chat denied: Message Length > %d", to, flags.maxChatLength)
        throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s chat: %s", to, message)
    addEvent(ChatEvent(this, to, message))
    if (switchTrivia) {
      if (!trivia!!.isAnswered && trivia!!.isCorrect(message)) {
        trivia!!.addScore(to.name!!, to.socketAddress!!.address.hostAddress, message)
      }
    }
  }

  @Throws(CreateGameException::class, FloodException::class)
  fun createGame(user: KailleraUser, romName: String): KailleraGame = withLock {
    if (!user.loggedIn) {
      logger.atSevere().log("%s create game failed: Not logged in", user)
      throw CreateGameException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (user.game != null) {
      logger.atSevere().log("%s create game failed: already in game: %s", user, user.game)
      throw CreateGameException(
        EmuLang.getString("KailleraServerImpl.CreateGameErrorAlreadyInGame")
      )
    }
    if (
      flags.maxGameNameLength > 0 && romName.trim { it <= ' ' }.length > flags.maxGameNameLength
    ) {
      logger
        .atWarning()
        .log("%s create game denied: Rom Name Length > %d", user, flags.maxGameNameLength)
      throw CreateGameException(EmuLang.getString("KailleraServerImpl.CreateGameDeniedNameTooLong"))
    }
    if (romName.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s create game denied: Illegal characters in ROM name", user)
      throw CreateGameException(
        EmuLang.getString("KailleraServerImpl.CreateGameDeniedIllegalCharacters")
      )
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (access == AccessManager.ACCESS_NORMAL) {
      if (
        flags.createGameFloodTime > Duration.ZERO &&
          clock.now() - user.lastCreateGameTime < flags.createGameFloodTime
      ) {
        logger.atWarning().log("%s create game denied: Flood: %s", user, romName)
        throw FloodException(EmuLang.getString("KailleraServerImpl.CreateGameDeniedFloodControl"))
      }
      if (flags.maxGames > 0 && gamesMap.values.size >= flags.maxGames) {
        logger
          .atWarning()
          .log("%s create game denied: Over maximum of %d current games!", user, flags.maxGames)
        throw CreateGameException(
          EmuLang.getString("KailleraServerImpl.CreateGameDeniedMaxGames", flags.maxGames)
        )
      }
      val chars = romName.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atWarning().log("%s create game denied: Illegal characters in ROM name", user)
          throw CreateGameException(
            EmuLang.getString("KailleraServerImpl.CreateGameDeniedIllegalCharacters")
          )
        }
      }
      if (romName.trim { it <= ' ' }.isEmpty()) {
        logger.atWarning().log("%s create game denied: Rom Name Empty", user)
        throw CreateGameException(EmuLang.getString("KailleraServerImpl.CreateGameErrorEmptyName"))
      }
      if (!accessManager.isGameAllowed(romName)) {
        logger
          .atWarning()
          .log("%s create game denied: AccessManager denied game: %s", user, romName)
        throw CreateGameException(
          EmuLang.getString("KailleraServerImpl.CreateGameDeniedGameBanned")
        )
      }
    }
    val game: KailleraGame?
    val gameID = getNextGameID()
    game = KailleraGame(gameID, romName, owner = user, this, flags.gameBufferSize, flags, clock)
    gamesMap[gameID] = game
    addEvent(GameCreatedEvent(this, game))
    logger.atInfo().log("%s created: %s: %s", user, game, game.romName)
    try {
      user.joinGame(game.id)
    } catch (e: Exception) {
      // this shouldn't happen
      logger
        .atSevere()
        .withCause(e)
        .log("Caught exception while making owner join game! This shouldn't happen!")
    }
    announce(
      EmuLang.getString(
        "KailleraServerImpl.UserCreatedGameAnnouncement",
        user.name!!,
        game.romName,
      ),
      false,
    )
    if (
      lookingForGameReporter.reportAndStartTimer(
        LookingForGameEvent(gameId = game.id, gameTitle = game.romName, user)
      )
    ) {
      user.game!!.announce(
        EmuLang.getString(
          "KailleraServerImpl.TweetPendingAnnouncement",
          flags.twitterBroadcastDelay.inWholeSeconds,
        ),
        user,
      )
    }
    return game
  }

  @Throws(CloseGameException::class)
  fun closeGame(game: KailleraGame, user: KailleraUser) = withLock {
    if (!user.loggedIn) {
      logger.atSevere().log("%s close %s failed: Not logged in", user, game)
      throw CloseGameException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (!gamesMap.containsKey(game.id)) {
      logger.atSevere().log("%s close %s failed: not in list: %s", user, game, game)
      return
    }
    game.close(user)
    gamesMap.remove(game.id)
    logger.atInfo().log("%s closed: %s", user, game)
    addEvent(GameClosedEvent(this, game))
  }

  fun checkMe(user: KailleraUser, message: String): Boolean {
    // >>>>>>>>>>>>>>>>>>>>
    var message = message
    if (!user.loggedIn) {
      logger.atSevere().log("%s chat failed: Not logged in", user)
      return false
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(user.socketAddress!!.address)
    ) {
      logger.atWarning().log("%s /me: Silenced: %s", user, message)
      return false
    }

    // if (access == AccessManager.ACCESS_NORMAL && flags.getChatFloodTime() > 0 &&
    // (System.currentTimeMillis()
    // - ((KailleraUser) user).getLastChatTime()) < (flags.getChatFloodTime() * 1000))
    // {
    //	logger.atWarning().log(user + " /me denied: Flood: " + message);
    //	return false;
    // }
    if (message == ":USER_COMMAND") {
      return false
    }
    message = message.trim { it <= ' ' }
    if (message.isBlank()) return false
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = message.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atWarning().log("%s /me: Illegal characters in message", user)
          return false
        }
      }
      if (flags.maxChatLength > 0 && message.length > flags.maxChatLength) {
        logger.atWarning().log("%s /me denied: Message Length > %d", user, flags.maxChatLength)
        return false
      }
    }
    return true
  }

  fun announce(message: String, gamesAlso: Boolean, targetUser: KailleraUser? = null) {
    if (targetUser == null) {
      usersMap.values
        .asSequence()
        .filter { it.loggedIn }
        .forEach { kailleraUser ->
          kailleraUser.queueEvent(InfoMessageEvent(kailleraUser, message))

          if (gamesAlso && kailleraUser.game != null) {
            kailleraUser.game!!.announce(message, kailleraUser)
            Thread.yield()
          }
        }
    } else {
      if (gamesAlso) { //   /msg and /me commands
        usersMap.values
          .asSequence()
          .filter { it.loggedIn }
          .forEach { kailleraUser ->
            val access = accessManager.getAccess(targetUser.connectSocketAddress.address)
            if (access < AccessManager.ACCESS_ADMIN) {
              if (
                !kailleraUser.searchIgnoredUsers(
                  targetUser.connectSocketAddress.address.hostAddress
                )
              )
                kailleraUser.queueEvent(InfoMessageEvent(kailleraUser, message))
            } else {
              kailleraUser.queueEvent(InfoMessageEvent(kailleraUser, message))
            }

            /*//SF MOD
            if(gamesAlso){
              if(kailleraUser.getGame() != null){
                kailleraUser.getGame().announce(announcement, kailleraUser);
                Thread.yield();
              }
            }
            */
          }
      } else {
        targetUser.queueEvent(InfoMessageEvent(targetUser, message))
      }
    }
  }

  fun addEvent(event: KailleraEvent) {
    for (user in usersMap.values) {
      if (user.loggedIn) {
        if (user.status != UserStatus.IDLE) {
          if (user.ignoringUnnecessaryServerActivity) {
            when (event) {
              is GameDataEvent -> user.queueEvent(event)
              is ChatEvent,
              is UserJoinedEvent,
              is UserQuitEvent,
              is GameStatusChangedEvent,
              is GameClosedEvent,
              is GameCreatedEvent -> continue
              else -> user.queueEvent(event)
            }
          } else {
            user.queueEvent(event)
          }
        } else {
          user.queueEvent(event)
        }
      } else {
        logger.atFine().log("%s: not adding event, not logged in: %s", user, event)
      }
    }
  }

  private fun run() {
    try {
      // Identify and repair games in a frozen state, where one user left and the server is waiting
      // on input to fan-out.
      for (game in gamesMap.values) {
        if (game.waitingOnData && game.status == GameStatus.PLAYING) {
          for (player in game.players) {
            if (
              player.status == UserStatus.IDLE &&
                game.waitingOnPlayerNumber[player.playerNumber - 1]
            ) {
              game.maybeSendData(usersMap[player.id]!!)
              break
            }
          }
        }
      }
      if (usersMap.isEmpty()) return
      for (user in usersMap.values) {
        val access = accessManager.getAccess(user.connectSocketAddress.address)
        user.accessLevel = access

        // LagStat
        if (user.loggedIn) {
          val game = user.game
          if (game != null && game.status == GameStatus.PLAYING && !game.startTimeout) {
            val stt = game.startTimeoutTime
            if (stt != null && clock.now() - stt >= 15.seconds) {
              game.players.forEach { it.resetLag() }
              game.resetLag()
              game.startTimeout = true
            }
          }
        }
        if (!user.loggedIn && clock.now() - user.connectTime > flags.maxPing * 15) {
          logger
            .atFine()
            .log("%s Timeout: User didn't successfully log in, removing stale entry", user)
          usersMap.remove(user.id)
        } else if (user.loggedIn && user.isDead) {
          logger
            .atInfo()
            .log(
              "%s Timeout: User likely disconnected from server without sending a Quit message.",
              user,
            )
          try {
            quit(user, EmuLang.getString("KailleraServerImpl.ForcedQuitPingTimeout"))
          } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error forcing %s quit for keepalive timeout!", user)
          }
        } else if (
          flags.idleTimeout.isPositive() &&
            access == AccessManager.ACCESS_NORMAL &&
            user.loggedIn &&
            user.isIdleForTooLong
        ) {
          logger.atInfo().log("%s inactivity timeout! (idle for too long)", user)
          try {
            quit(user, EmuLang.getString("KailleraServerImpl.ForcedQuitInactivityTimeout"))
          } catch (e: Exception) {
            logger
              .atSevere()
              .withCause(e)
              .log("Error forcing %s quit for inactivity timeout!", user)
          }
        } else if (user.loggedIn && access < AccessManager.ACCESS_NORMAL) {
          logger.atInfo().log("%s banned!", user)
          try {
            quit(user, EmuLang.getString("KailleraServerImpl.ForcedQuitBanned"))
          } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error forcing %s quit because banned!", user)
          }
        } else if (
          user.loggedIn &&
            access == AccessManager.ACCESS_NORMAL &&
            !accessManager.isEmulatorAllowed(user.clientType!!)
        ) {
          logger.atInfo().log("%s: emulator restricted!", user)
          try {
            quit(user, EmuLang.getString("KailleraServerImpl.ForcedQuitEmulatorRestricted"))
          } catch (e: Exception) {
            logger
              .atSevere()
              .withCause(e)
              .log("Error forcing %s quit because emulator restricted!", user)
          }
        } else {}
      }
    } catch (e: Throwable) {
      logger.atWarning().withCause(e).log("Failure during KailleraServer.run()")
    }
  }

  init {
    flags.allowedConnectionTypes.forEach { type ->
      val ct = type.toInt()
      allowedConnectionTypes[ct] = true
    }
    if (flags.touchKaillera) {
      this.statsCollector = statsCollector
    }
    metrics.register(
      MetricRegistry.name(this.javaClass, "users", "idle"),
      Gauge { usersMap.values.count { it.status == UserStatus.IDLE } },
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "users", "playing"),
      Gauge { usersMap.values.count { it.status == UserStatus.PLAYING } },
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "games", "waiting"),
      Gauge { gamesMap.values.count { it.status == GameStatus.WAITING } },
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "games", "playing"),
      Gauge { gamesMap.values.count { it.status == GameStatus.PLAYING } },
    )

    userActionsExecutor.submit {
      logger.atFine().log("Waiting for KailleraEvents")
      try {
        while (!stopFlag.get()) {
          val userToEvent: Pair<KailleraUser, KailleraEvent>? = eventQueue.poll(5, TimeUnit.SECONDS)
          if (userToEvent == null) {
            if (Thread.interrupted()) break

            continue
          }
          try {
            userToEvent.first.doEvent(userToEvent.second)
          } catch (e: RuntimeException) {
            logger.atSevere().withCause(e).log("%s thread caught unexpected exception!", this)
          }
        }
      } catch (e: InterruptedException) {
        logger.atSevere().withCause(e).log("%s thread interrupted!", this)
      } finally {
        logger.atFine().log("Done waiting for KailleraEvents")
      }
    }
  }

  private var stopFlag = AtomicBoolean(false)

  private val o = Object()

  /** Helper function to avoid one level of indentation. */
  private inline fun <T> withLock(action: () -> T): T = synchronized(o) { action() }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
