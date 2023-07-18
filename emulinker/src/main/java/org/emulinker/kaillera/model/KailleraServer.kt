package org.emulinker.kaillera.model

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Throws
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.lookingforgame.LookingForGameEvent
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.event.ChatEvent
import org.emulinker.kaillera.model.event.ConnectedEvent
import org.emulinker.kaillera.model.event.GameClosedEvent
import org.emulinker.kaillera.model.event.GameCreatedEvent
import org.emulinker.kaillera.model.event.InfoMessageEvent
import org.emulinker.kaillera.model.event.KailleraEventListener
import org.emulinker.kaillera.model.event.ServerEvent
import org.emulinker.kaillera.model.event.UserJoinedEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.exception.*
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.kaillera.model.impl.Trivia
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.EmuUtil.threadSleep

/** Holds server-wide state. */
// TODO(nue): This looks like this file contains a ton of unnecessary processing and runs basically
// continuously.
@Singleton
class KailleraServer
@Inject
internal constructor(
  threadPool: ThreadPoolExecutor,
  val accessManager: AccessManager,
  private val flags: RuntimeFlags,
  statsCollector: StatsCollector?,
  val releaseInfo: ReleaseInfo,
  private val autoFireDetectorFactory: AutoFireDetectorFactory,
  private val lookingForGameReporter: TwitterBroadcaster,
  metrics: MetricRegistry,
  private val timer: Timer,
) {

  private var allowedConnectionTypes = BooleanArray(7)
  private val loginMessages: List<String>
  private var connectionCounter = 1
  private var gameCounter = 1

  var statsCollector: StatsCollector? = null

  private val usersMap: MutableMap<Int, KailleraUser> = ConcurrentHashMap(flags.maxUsers)
  val users = usersMap.values

  var gamesMap: MutableMap<Int, KailleraGameImpl> = ConcurrentHashMap(flags.maxGames)
  val games = gamesMap.values

  var trivia: Trivia? = null

  var triviaThread: Thread? = null

  var switchTrivia = false

  fun getUser(userID: Int): KailleraUser? {
    return usersMap[userID]
  }

  fun getGame(gameID: Int): KailleraGame? {
    return gamesMap[gameID]
  }

  val maxPing: Int = flags.maxPing
  val maxUsers: Int = flags.maxUsers
  val maxGames: Int = flags.maxGames

  val allowSinglePlayer = flags.allowSinglePlayer
  private val maxUserNameLength: Int = flags.maxUserNameLength
  val maxGameChatLength = flags.maxGameChatLength
  private val maxClientNameLength: Int = flags.maxClientNameLength

  private var timerTask: TimerTask? = null

  /** Basically the [CoroutineScope] to be used for all asynchronous actions. */
  val coroutineScope = CoroutineScope(threadPool.asCoroutineDispatcher())

  override fun toString(): String {
    return String.format(
      "KailleraServer[numUsers=%d numGames=%d]",
      users.size,
      games.size,
    )
  }

  @Synchronized
  fun start() {
    //    timerTask =
    //      timer.schedule(
    //        delay = (flags.maxPing * 3).milliseconds.inWholeMilliseconds,
    //        period = (flags.maxPing * 3).milliseconds.inWholeMilliseconds
    //      ) {
    //        run()
    //      }
  }

  @Synchronized
  fun stop() {
    coroutineScope.cancel()
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

  fun getAutoFireDetector(game: KailleraGame?): AutoFireDetector {
    return autoFireDetectorFactory.getInstance(game!!, flags.gameAutoFireSensitivity)
  }

  @Synchronized
  @Throws(ServerFullException::class, NewConnectionException::class)
  fun newConnection(
    clientSocketAddress: InetSocketAddress?,
    protocol: String?,
    listener: KailleraEventListener?
  ): KailleraUser {
    // we'll assume at this point that ConnectController has already asked AccessManager if this IP
    // is banned, so no need to do it again here
    logger
      .atFine()
      .log(
        "Processing connection request from %s",
        EmuUtil.formatSocketAddress(clientSocketAddress!!)
      )
    val access = accessManager.getAccess(clientSocketAddress.address)

    // admins will be allowed in even if the server is full
    if (flags.maxUsers > 0 && usersMap.size >= maxUsers && access <= AccessManager.ACCESS_NORMAL) {
      logger
        .atWarning()
        .log(
          "Connection from %s denied: Server is full!",
          EmuUtil.formatSocketAddress(clientSocketAddress)
        )
      throw ServerFullException(EmuLang.getString("KailleraServerImpl.LoginDeniedServerFull"))
    }
    val userID = getNextUserID()
    val user = KailleraUser(userID, protocol!!, clientSocketAddress, listener!!, this, flags)
    user.status = UserStatus.CONNECTING
    logger
      .atInfo()
      .log(
        "%s attempting new connection using protocol %s from %s",
        user,
        protocol,
        EmuUtil.formatSocketAddress(clientSocketAddress)
      )
    usersMap[userID] = user
    return user
  }

  @Synchronized
  @Throws(
    PingTimeException::class,
    ClientAddressException::class,
    ConnectionTypeException::class,
    UserNameException::class,
    LoginException::class
  )
  fun login(user: KailleraUser?) {
    val userImpl = user
    val loginDelay = System.currentTimeMillis() - user!!.connectTime
    logger
      .atInfo()
      .log(
        "%s: login request: delay=%d ms, clientAddress=%s, name=%s, ping=%d, client=%s, connection=%s",
        user,
        loginDelay,
        EmuUtil.formatSocketAddress(user.socketAddress!!),
        user.name,
        user.ping,
        user.clientType,
        user.connectionType
      )
    if (user.loggedIn) {
      logger.atWarning().log("%s login denied: Already logged in!", user)
      throw LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedAlreadyLoggedIn"))
    }
    val userListKey = user.id
    val u: KailleraUser? = usersMap[userListKey]
    if (u == null) {
      logger.atWarning().log("%s login denied: Connection timed out!", user)
      throw LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedConnectionTimedOut"))
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (access < AccessManager.ACCESS_NORMAL) {
      logger.atInfo().log("%s login denied: Access denied", user)
      usersMap.remove(userListKey)
      throw LoginException(EmuLang.getString("KailleraServerImpl.LoginDeniedAccessDenied"))
    }
    if (access == AccessManager.ACCESS_NORMAL && maxPing > 0 && user.ping > maxPing) {
      logger.atInfo().log("%s login denied: Ping %d > %d", user, user.ping, maxPing)
      usersMap.remove(userListKey)
      throw PingTimeException(
        EmuLang.getString(
          "KailleraServerImpl.LoginDeniedPingTooHigh",
          user.ping.toString() + " > " + maxPing
        )
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        !allowedConnectionTypes[user.connectionType.byteValue.toInt()]
    ) {
      logger.atInfo().log("%s login denied: Connection %s Not Allowed", user, user.connectionType)
      usersMap.remove(userListKey)
      throw LoginException(
        EmuLang.getString("KailleraServerImpl.LoginDeniedConnectionTypeDenied", user.connectionType)
      )
    }
    if (user.ping < 0) {
      logger.atWarning().log("%s login denied: Invalid ping: %d", user, user.ping)
      usersMap.remove(userListKey)
      throw PingTimeException(
        EmuLang.getString("KailleraServerImpl.LoginErrorInvalidPing", user.ping)
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL && user.name.isNullOrEmpty() || user.name!!.isBlank()
    ) {
      logger.atInfo().log("%s login denied: Empty UserName", user)
      usersMap.remove(userListKey)
      throw UserNameException(EmuLang.getString("KailleraServerImpl.LoginDeniedUserNameEmpty"))
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
      throw UserNameException(
        EmuLang.getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
      )
    }

    // access == AccessManager.ACCESS_NORMAL &&
    if (flags.maxUserNameLength > 0 && user.name!!.length > maxUserNameLength) {
      logger.atInfo().log("%s login denied: UserName Length > %d", user, maxUserNameLength)
      usersMap.remove(userListKey)
      throw UserNameException(EmuLang.getString("KailleraServerImpl.LoginDeniedUserNameTooLong"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.maxClientNameLength > 0 &&
        user.clientType!!.length > maxClientNameLength
    ) {
      logger.atInfo().log("%s login denied: Client Name Length > %d", user, maxClientNameLength)
      usersMap.remove(userListKey)
      throw UserNameException(
        EmuLang.getString("KailleraServerImpl.LoginDeniedEmulatorNameTooLong")
      )
    }
    if (user.clientType!!.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s login denied: Illegal characters in EmulatorName", user)
      usersMap.remove(userListKey)
      throw UserNameException("Illegal characters in Emulator Name")
    }
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = user.name!!.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atInfo().log("%s login denied: Illegal characters in UserName", user)
          usersMap.remove(userListKey)
          throw UserNameException(
            EmuLang.getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
          )
        }
      }
    }
    if (u.status != UserStatus.CONNECTING) {
      usersMap.remove(userListKey)
      logger.atWarning().log("%s login denied: Invalid status=%s", user, u.status)
      throw LoginException(
        EmuLang.getString("KailleraServerImpl.LoginErrorInvalidStatus", u.status)
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
          user.socketAddress!!.address.hostAddress
        )
      throw ClientAddressException(
        EmuLang.getString("KailleraServerImpl.LoginDeniedAddressMatchError")
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL && !accessManager.isEmulatorAllowed(user.clientType)
    ) {
      logger
        .atInfo()
        .log("%s login denied: AccessManager denied emulator: %s", user, user.clientType)
      usersMap.remove(userListKey)
      throw LoginException(
        EmuLang.getString("KailleraServerImpl.LoginDeniedEmulatorRestricted", user.clientType)
      )
    }
    for (u2 in users) {
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
          throw ClientAddressException("Duplicating names is not allowed: " + u2.name)
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
          throw ClientAddressException(
            EmuLang.getString("KailleraServerImpl.LoginDeniedAlreadyLoggedInAs", u2.name)
          )
        }
      }
    }

    // passed all checks
    userImpl!!.accessLevel = access
    userImpl.status = UserStatus.IDLE
    userImpl.loggedIn = true
    usersMap[userListKey] = userImpl
    userImpl.addEvent(ConnectedEvent(this, user))
    threadSleep(20.milliseconds)
    for (loginMessage in loginMessages) {
      userImpl.addEvent(InfoMessageEvent(user, loginMessage))
      threadSleep(20.milliseconds)
    }
    userImpl.addEvent(
      InfoMessageEvent(
        user,
        "${releaseInfo.productName} v${releaseInfo.version}: ${releaseInfo.websiteString}"
      )
    )
    threadSleep(20.milliseconds)
    if (access > AccessManager.ACCESS_NORMAL) {
      logger
        .atInfo()
        .log("%s logged in successfully with %s access!", user, AccessManager.ACCESS_NAMES[access])
    } else {
      logger.atInfo().log("%s logged in successfully", user)
    }

    // this is fairly ugly
    if (user.isEmuLinkerClient) {
      userImpl.addEvent(InfoMessageEvent(user, ":ACCESS=" + userImpl.accessStr))
      if (access >= AccessManager.ACCESS_SUPERADMIN) {
        var sb = StringBuilder()
        sb.append(":USERINFO=")
        var sbCount = 0
        for (u3 in users) {
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
            (user as KailleraUser?)!!.addEvent(InfoMessageEvent(user, sb.toString()))
            sb = StringBuilder()
            sb.append(":USERINFO=")
            sbCount = 0
            threadSleep(100.milliseconds)
          }
        }
        if (sbCount > 0) (user as KailleraUser?)!!.addEvent(InfoMessageEvent(user, sb.toString()))
        threadSleep(100.milliseconds)
      }
    }
    threadSleep(20.milliseconds)
    if (access >= AccessManager.ACCESS_ADMIN) {
      userImpl.addEvent(
        InfoMessageEvent(user, EmuLang.getString("KailleraServerImpl.AdminWelcomeMessage"))
      )
      // Display messages to admins if they exist.
      AppModule.messagesToAdmins.forEach { message ->
        threadSleep(20.milliseconds)
        userImpl.addEvent(InfoMessageEvent(user, message))
      }
    }
    addEvent(UserJoinedEvent(this, user))
    threadSleep(20.milliseconds)
    val announcement = accessManager.getAnnouncement(user.socketAddress!!.address)
    if (announcement != null)
      announce(
        announcement,
        false,
      )
  }

  @Synchronized
  @Throws(
    QuitException::class,
    DropGameException::class,
    QuitGameException::class,
    CloseGameException::class
  )
  fun quit(user: KailleraUser?, message: String?) {
    lookingForGameReporter.cancelActionsForUser(user!!.id)
    if (!user.loggedIn) {
      usersMap.remove(user.id)
      logger.atSevere().log("%s quit failed: Not logged in", user)
      throw QuitException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (usersMap.remove(user.id) == null)
      logger.atSevere().log("%s quit failed: not in user list", user)
    val userGame = (user as KailleraUser?)!!.game
    if (userGame != null) user.quitGame()
    var quitMsg = message!!.trim { it <= ' ' }
    if (
      quitMsg.isBlank() ||
        (flags.maxQuitMessageLength > 0 && quitMsg.length > flags.maxQuitMessageLength)
    )
      quitMsg = EmuLang.getString("KailleraServerImpl.StandardQuitMessage")
    val access = user.server.accessManager.getAccess(user.socketAddress!!.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        user.server.accessManager.isSilenced(user.socketAddress!!.address)
    ) {
      quitMsg = "www.EmuLinker.org"
    }
    logger.atInfo().log("%s quit: %s", user, quitMsg)
    val quitEvent = UserQuitEvent(this, user, quitMsg)
    addEvent(quitEvent)
    (user as KailleraUser?)!!.addEvent(quitEvent)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  fun chat(user: KailleraUser?, message: String?) {
    var message = message
    if (!user!!.loggedIn) {
      logger.atSevere().log("%s chat failed: Not logged in", user)
      throw ChatException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(user.socketAddress!!.address)
    ) {
      logger.atWarning().log("%s chat denied: Silenced: %s", user, message)
      throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedSilenced"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.chatFloodTime > 0 &&
        (System.currentTimeMillis() - (user as KailleraUser?)!!.lastChatTime <
          flags.chatFloodTime * 1000)
    ) {
      logger.atWarning().log("%s chat denied: Flood: %s", user, message)
      throw FloodException(EmuLang.getString("KailleraServerImpl.ChatDeniedFloodControl"))
    }
    if (message == ":USER_COMMAND") {
      return
    }
    message = message!!.trim { it <= ' ' }
    if (message.isNullOrBlank() || message.startsWith("�") || message.startsWith("�")) return
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = message.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atWarning().log("%s chat denied: Illegal characters in message", user)
          throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedIllegalCharacters"))
        }
      }
      if (flags.maxChatLength > 0 && message.length > flags.maxChatLength) {
        logger.atWarning().log("%s chat denied: Message Length > %d", user, flags.maxChatLength)
        throw ChatException(EmuLang.getString("KailleraServerImpl.ChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s chat: %s", user, message)
    addEvent(ChatEvent(this, user, message))
    if (switchTrivia) {
      if (!trivia!!.isAnswered && trivia!!.isCorrect(message)) {
        trivia!!.addScore(user.name!!, user.socketAddress!!.address.hostAddress, message)
      }
    }
  }

  @Synchronized
  @Throws(CreateGameException::class, FloodException::class)
  fun createGame(user: KailleraUser?, romName: String?): KailleraGame {
    if (!user!!.loggedIn) {
      logger.atSevere().log("%s create game failed: Not logged in", user)
      throw CreateGameException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if ((user as KailleraUser?)!!.game != null) {
      logger
        .atSevere()
        .log("%s create game failed: already in game: %s", user, (user as KailleraUser?)!!.game)
      throw CreateGameException(
        EmuLang.getString("KailleraServerImpl.CreateGameErrorAlreadyInGame")
      )
    }
    if (
      flags.maxGameNameLength > 0 && romName!!.trim { it <= ' ' }.length > flags.maxGameNameLength
    ) {
      logger
        .atWarning()
        .log("%s create game denied: Rom Name Length > %d", user, flags.maxGameNameLength)
      throw CreateGameException(EmuLang.getString("KailleraServerImpl.CreateGameDeniedNameTooLong"))
    }
    if (romName!!.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s create game denied: Illegal characters in ROM name", user)
      throw CreateGameException(
        EmuLang.getString("KailleraServerImpl.CreateGameDeniedIllegalCharacters")
      )
    }
    val access = accessManager.getAccess(user.socketAddress!!.address)
    if (access == AccessManager.ACCESS_NORMAL) {
      if (
        flags.createGameFloodTime > 0 &&
          System.currentTimeMillis() - (user as KailleraUser?)!!.lastCreateGameTime <
            flags.createGameFloodTime * 1000
      ) {
        logger.atWarning().log("%s create game denied: Flood: %s", user, romName)
        throw FloodException(EmuLang.getString("KailleraServerImpl.CreateGameDeniedFloodControl"))
      }
      if (flags.maxGames > 0 && games.size >= flags.maxGames) {
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
    var game: KailleraGameImpl? = null
    val gameID = getNextGameID()
    game = KailleraGameImpl(gameID, romName, (user as KailleraUser?)!!, this, flags.gameBufferSize)
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
      EmuLang.getString("KailleraServerImpl.UserCreatedGameAnnouncement", user.name, game.romName),
      false,
    )
    if (
      lookingForGameReporter.reportAndStartTimer(
        LookingForGameEvent(/* gameId= */ game.id, /* gameTitle= */ game.romName, /* user= */ user)
      )
    ) {
      user.game!!.announce(
        EmuLang.getString(
          "KailleraServerImpl.TweetPendingAnnouncement",
          flags.twitterBroadcastDelay.inWholeSeconds
        ),
        user
      )
    }
    return game
  }

  @Synchronized
  @Throws(CloseGameException::class)
  fun closeGame(game: KailleraGame, user: KailleraUser) {
    if (!user.loggedIn) {
      logger.atSevere().log("%s close %s failed: Not logged in", user, game)
      throw CloseGameException(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (!gamesMap.containsKey(game.id)) {
      logger.atSevere().log("%s close %s failed: not in list: %s", user, game, game)
      return
    }
    (game as KailleraGameImpl).close(user)
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

  fun announceInGame(announcement: String?, user: KailleraUser) {
    user.game!!.announce(announcement!!, user)
  }

  fun announce(message: String, gamesAlso: Boolean) {
    announce(message, gamesAlso, targetUser = null)
  }

  fun announce(message: String, gamesAlso: Boolean, targetUser: KailleraUser?) {
    if (targetUser == null) {
      users
        .asSequence()
        .filter { it.loggedIn }
        .forEach { kailleraUser ->
          kailleraUser.addEvent(InfoMessageEvent(kailleraUser, message))

          if (gamesAlso && kailleraUser.game != null) {
            kailleraUser.game!!.announce(message, kailleraUser)
            Thread.yield()
          }
        }
    } else {
      if (gamesAlso) { //   /msg and /me commands
        users
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
                kailleraUser.addEvent(InfoMessageEvent(kailleraUser, message))
            } else {
              kailleraUser.addEvent(InfoMessageEvent(kailleraUser, message))
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
        targetUser.addEvent(InfoMessageEvent(targetUser, message))
      }
    }
  }

  fun addEvent(event: ServerEvent) {
    for (user in usersMap.values) {
      if (user.loggedIn) {
        if (user.status != UserStatus.IDLE) {
          if (user.ignoringUnnecessaryServerActivity) {
            // TODO(nue): Get rid of this bad use of toString.
            if (event.toString() == "GameDataEvent") user.addEvent(event)
            else if (event.toString() == "ChatEvent") continue
            else if (event.toString() == "UserJoinedEvent") continue
            else if (event.toString() == "UserQuitEvent") continue
            else if (event.toString() == "GameStatusChangedEvent") continue
            else if (event.toString() == "GameClosedEvent") continue
            else if (event.toString() == "GameCreatedEvent") continue else user.addEvent(event)
          } else {
            user.addEvent(event)
          }
        } else {
          user.addEvent(event)
        }
      } else {
        logger.atFine().log("%s: not adding event, not logged in: %s", user, event)
      }
    }
  }

  fun run() {
    try {
      if (usersMap.isEmpty()) return
      for (user in users) {
        synchronized(user) {
          val access = accessManager.getAccess(user.connectSocketAddress.address)
          user.accessLevel = access

          // LagStat
          if (user.loggedIn) {
            if (
              user.game != null &&
                user.game!!.status == GameStatus.PLAYING &&
                !user.game!!.startTimeout
            ) {
              if (System.currentTimeMillis() - user.game!!.startTimeoutTime > 15000) {
                user.game!!.startTimeout = true
              }
            }
          }
          if (
            !user.loggedIn && System.currentTimeMillis() - user.connectTime > flags.maxPing * 15
          ) {
            logger.atInfo().log("%s connection timeout!", user)
            usersMap.remove(user.id)
          } else if (
            user.loggedIn &&
              System.currentTimeMillis() - user.lastKeepAlive >
                flags.keepAliveTimeout.inWholeMilliseconds
          ) {
            logger.atInfo().log("%s keepalive timeout!", user)
            try {
              quit(user, EmuLang.getString("KailleraServerImpl.ForcedQuitPingTimeout"))
            } catch (e: Exception) {
              logger
                .atSevere()
                .withCause(e)
                .log("Error forcing %s quit for keepalive timeout!", user)
            }
          } else if (
            flags.idleTimeout.isPositive() &&
              access == AccessManager.ACCESS_NORMAL &&
              user.loggedIn &&
              (Instant.now().toEpochMilli() - user.lastActivity >
                flags.idleTimeout.inWholeMilliseconds)
          ) {
            logger.atInfo().log("%s inactivity timeout!", user)
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
              !accessManager.isEmulatorAllowed(user.clientType)
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
      }
    } catch (e: Throwable) {
      logger.atWarning().withCause(e).log("Failure during KailleraServer.run()")
    }
  }

  init {
    val loginMessagesBuilder = mutableListOf<String>()
    var i = 1
    while (EmuLang.hasString("KailleraServerImpl.LoginMessage.$i")) {
      loginMessagesBuilder.add(EmuLang.getString("KailleraServerImpl.LoginMessage.$i"))
      i++
    }
    loginMessages = loginMessagesBuilder
    flags.connectionTypes.forEach { type ->
      val ct = type.toInt()
      allowedConnectionTypes[ct] = true
    }
    if (flags.touchKaillera) {
      this.statsCollector = statsCollector
    }
    metrics.register(
      MetricRegistry.name(this.javaClass, "users", "idle"),
      Gauge { usersMap.values.count { it.status == UserStatus.IDLE } }
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "users", "playing"),
      Gauge { usersMap.values.count { it.status == UserStatus.PLAYING } }
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "games", "waiting"),
      Gauge { gamesMap.values.count { it.status == GameStatus.WAITING } }
    )
    metrics.register(
      MetricRegistry.name(this.javaClass, "games", "playing"),
      Gauge { gamesMap.values.count { it.status == GameStatus.PLAYING } }
    )
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
