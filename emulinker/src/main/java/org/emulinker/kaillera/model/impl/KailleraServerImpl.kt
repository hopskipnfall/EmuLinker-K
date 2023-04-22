package org.emulinker.kaillera.model.impl

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.access.AccessManager.Companion.ACCESS_NAMES
import org.emulinker.kaillera.controller.v086.V086Utils.toKtorAddress
import org.emulinker.kaillera.lookingforgame.LookingForGameEvent
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.*
import org.emulinker.kaillera.model.event.*
import org.emulinker.kaillera.model.exception.*
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.EmuLang.getString
import org.emulinker.util.EmuLang.hasString
import org.emulinker.util.EmuUtil.formatSocketAddress
import org.emulinker.util.Executable

@Singleton
class KailleraServerImpl
@Inject
internal constructor(
  override val accessManager: AccessManager,
  private val flags: RuntimeFlags,
  statsCollector: StatsCollector?,
  override val releaseInfo: ReleaseInfo,
  private val autoFireDetectorFactory: AutoFireDetectorFactory,
  private val lookingForGameReporter: TwitterBroadcaster,
  metrics: MetricRegistry
) : KailleraServer, Executable {
  /** [CoroutineScope] for long-running actions. */
  private val kailleraServerCoroutineScope =
    CoroutineScope(Dispatchers.IO) + CoroutineName("KailleraServerScope")

  private var allowedConnectionTypes = BooleanArray(7)
  private val loginMessages: List<String>
  private var stopFlag = false
  override var threadIsActive = false
    private set
  private var connectionCounter = 1
  private var gameCounter = 1

  var statsCollector: StatsCollector? = null

  private val usersMap: MutableMap<Int, KailleraUserImpl> = ConcurrentHashMap(flags.maxUsers)
  override val users = usersMap.values

  var gamesMap: MutableMap<Int, KailleraGameImpl> = ConcurrentHashMap(flags.maxGames)
  override val games = gamesMap.values

  override var trivia: Trivia? = null

  var triviaThread: Thread? = null

  override var switchTrivia = false

  override fun getUser(userID: Int): KailleraUser? {
    return usersMap[userID]
  }

  override fun getGame(gameID: Int): KailleraGame? {
    return gamesMap[gameID]
  }

  override val maxPing = flags.maxPing
  override val maxUsers = flags.maxUsers
  override val maxGames = flags.maxGames

  val allowSinglePlayer = flags.allowSinglePlayer
  private val maxUserNameLength = flags.maxUserNameLength
  val maxGameChatLength = flags.maxGameChatLength
  private val maxClientNameLength = flags.maxClientNameLength

  override fun toString(): String {
    return String.format(
      "KailleraServerImpl[numUsers=%d numGames=%d isRunning=%b]",
      users.size,
      games.size,
      threadIsActive
    )
  }

  override suspend fun stop() {
    logger.atFine().log("KailleraServer thread received stop request!")
    if (!threadIsActive) {
      logger.atFine().log("KailleraServer thread stop request ignored: not running!")
      return
    }
    stopFlag = true
    usersMap.values.forEach { it.stop() }
    usersMap.clear()
    gamesMap.clear()
    kailleraServerCoroutineScope.cancel()
  }

  // not synchronized because I know the caller will be thread safe
  private fun getNextUserID(): Int {
    if (connectionCounter > 0xFFFF) {
      connectionCounter = 1
    }
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
  override fun newConnection(
    clientSocketAddress: InetSocketAddress,
    protocol: String,
    listener: KailleraEventListener
  ): KailleraUser {
    // we'll assume at this point that ConnectController has already asked AccessManager if this IP
    // is banned, so no need to do it again here
    logger
      .atFine()
      .log(
        "Processing connection request from %s",
        lazy { formatSocketAddress(clientSocketAddress) }
      )
    val access = accessManager.getAccess(clientSocketAddress.address)

    // admins will be allowed in even if the server is full
    if (flags.maxUsers > 0 && usersMap.size >= maxUsers && access <= AccessManager.ACCESS_NORMAL) {
      logger
        .atWarning()
        .log("Connection from %s denied: Server is full!", formatSocketAddress(clientSocketAddress))
      throw ServerFullException(getString("KailleraServerImpl.LoginDeniedServerFull"))
    }
    val userId = getNextUserID()
    val user =
      KailleraUserImpl(
        UserData(userId, name = "[PENDING]", clientSocketAddress.toKtorAddress()),
        protocol,
        clientSocketAddress,
        listener,
        this,
        flags
      )
    user.status = UserStatus.CONNECTING
    logger
      .atInfo()
      .log(
        "%s attempting new connection using protocol %s from %s",
        user,
        protocol,
        formatSocketAddress(clientSocketAddress)
      )
    usersMap[userId] = user

    // look for the infinite loop inside of the user class
    kailleraServerCoroutineScope.launch { user.run(coroutineContext) }
    return user
  }

  @Throws(
    PingTimeException::class,
    ClientAddressException::class,
    ConnectionTypeException::class,
    UserNameException::class,
    LoginException::class
  )
  override suspend fun login(user: KailleraUser) {
    val userImpl = user as KailleraUserImpl
    logger
      .atInfo()
      .log(
        "%s: login request: delay=%s ms, clientAddress=%s, name=%s, ping=%d, client=%s, connection=%s",
        user,
        Duration.between(user.connectTime, Instant.now()),
        formatSocketAddress(user.socketAddress),
        user.userData.name,
        user.ping,
        user.clientType,
        user.connectionType
      )
    if (user.loggedIn) {
      logger.atWarning().log("%s login denied: Already logged in!", user)
      throw LoginException(getString("KailleraServerImpl.LoginDeniedAlreadyLoggedIn"))
    }
    val userListKey = user.userData.id
    val u = usersMap[userListKey]
    if (u == null) {
      logger.atWarning().log("%s login denied: Connection timed out!", user)
      throw LoginException(getString("KailleraServerImpl.LoginDeniedConnectionTimedOut"))
    }
    val access = accessManager.getAccess(user.socketAddress.address)
    if (access < AccessManager.ACCESS_NORMAL) {
      logger.atInfo().log("%s login denied: Access denied", user)
      usersMap.remove(userListKey)
      throw LoginException(getString("KailleraServerImpl.LoginDeniedAccessDenied"))
    }
    if (access == AccessManager.ACCESS_NORMAL && maxPing > 0 && user.ping > maxPing) {
      logger.atInfo().log("%s login denied: Ping %d > %d", user, user.ping, maxPing)
      usersMap.remove(userListKey)
      throw PingTimeException(
        getString("KailleraServerImpl.LoginDeniedPingTooHigh", "${user.ping} > $maxPing")
      )
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        !allowedConnectionTypes[user.connectionType.byteValue.toInt()]
    ) {
      logger.atInfo().log("%s login denied: Connection %s Not Allowed", user, user.connectionType)
      usersMap.remove(userListKey)
      throw LoginException(
        getString("KailleraServerImpl.LoginDeniedConnectionTypeDenied", user.connectionType)
      )
    }
    if (user.ping < 0) {
      logger.atWarning().log("%s login denied: Invalid ping: %d", user, user.ping)
      usersMap.remove(userListKey)
      throw PingTimeException(getString("KailleraServerImpl.LoginErrorInvalidPing", user.ping))
    }
    if (
      access == AccessManager.ACCESS_NORMAL && user.userData.name.isEmpty() ||
        user.userData.name.isBlank()
    ) {
      logger.atInfo().log("%s login denied: Empty UserName", user)
      usersMap.remove(userListKey)
      throw UserNameException(getString("KailleraServerImpl.LoginDeniedUserNameEmpty"))
    }

    // new SF MOD - Username filter
    val nameLower = user.userData.name.lowercase(Locale.getDefault())
    if (
      user.userData.name == "Server" ||
        nameLower.contains("|") ||
        (access == AccessManager.ACCESS_NORMAL &&
          arrayOf("www.", "http://", "https://", "\\", "�", "�").any { nameLower.contains(it) })
    ) {
      logger.atInfo().log("%s login denied: Illegal characters in UserName", user)
      usersMap.remove(userListKey)
      throw UserNameException(
        getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
      )
    }

    // access == AccessManager.ACCESS_NORMAL &&
    if (flags.maxUserNameLength > 0 && user.userData.name.length > maxUserNameLength) {
      logger.atInfo().log("%s login denied: UserName Length > %s", user, maxUserNameLength)
      usersMap.remove(userListKey)
      throw UserNameException(getString("KailleraServerImpl.LoginDeniedUserNameTooLong"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.maxClientNameLength > 0 &&
        user.clientType!!.length > maxClientNameLength
    ) {
      logger.atInfo().log("%s login denied: Client Name Length > %s", user, maxClientNameLength)
      usersMap.remove(userListKey)
      throw UserNameException(getString("KailleraServerImpl.LoginDeniedEmulatorNameTooLong"))
    }
    if (user.clientType!!.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s login denied: Illegal characters in EmulatorName", user)
      usersMap.remove(userListKey)
      throw UserNameException("Illegal characters in Emulator Name")
    }
    if (access == AccessManager.ACCESS_NORMAL) {
      val chars = user.userData.name.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atInfo().log("%s login denied: Illegal characters in UserName", user)
          usersMap.remove(userListKey)
          throw UserNameException(
            getString("KailleraServerImpl.LoginDeniedIllegalCharactersInUserName")
          )
        }
      }
    }
    if (u.status != UserStatus.CONNECTING) {
      usersMap.remove(userListKey)
      logger.atWarning().log("%s login denied: Invalid status=%s", user, u.status)
      throw LoginException(getString("KailleraServerImpl.LoginErrorInvalidStatus", u.status))
    }
    if (u.connectSocketAddress.address != user.socketAddress.address) {
      usersMap.remove(userListKey)
      logger
        .atWarning()
        .log(
          "%s login denied: Connect address does not match login address: %s != %s",
          user,
          u.connectSocketAddress.address.hostAddress,
          user.socketAddress.address.hostAddress
        )
      throw ClientAddressException(getString("KailleraServerImpl.LoginDeniedAddressMatchError"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL && !accessManager.isEmulatorAllowed(user.clientType)
    ) {
      logger
        .atInfo()
        .log("%s login denied: AccessManager denied emulator: %s", user, user.clientType)
      usersMap.remove(userListKey)
      throw LoginException(
        getString("KailleraServerImpl.LoginDeniedEmulatorRestricted", user.clientType)
      )
    }
    for (u2 in users) {
      if (u2.loggedIn) {
        if (
          u2.userData.id != u.userData.id &&
            (u.connectSocketAddress.address == u2.connectSocketAddress.address) &&
            u.userData.name == u2.userData.name
        ) {
          // user is attempting to login more than once with the same name and address
          // logoff the old user and login the new one
          try {
            quit(u2, getString("KailleraServerImpl.ForcedQuitReconnected"))
          } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error forcing %s quit for reconnect!", u2)
          }
        } else if (
          u2.userData.id != u.userData.id &&
            u2.userData.name.lowercase(Locale.getDefault()).trim { it <= ' ' } ==
              u.userData.name.lowercase(Locale.getDefault()).trim { it <= ' ' }
        ) {
          usersMap.remove(userListKey)
          logger
            .atWarning()
            .log("%s login denied: Duplicating Names is not allowed! %s", user, u2.userData.name)
          throw ClientAddressException("Duplicating names is not allowed: " + u2.userData.name)
        }
        if (
          access == AccessManager.ACCESS_NORMAL &&
            u2.userData.id != u.userData.id &&
            (u.connectSocketAddress.address == u2.connectSocketAddress.address) &&
            u.userData.name != u2.userData.name &&
            !flags.allowMultipleConnections
        ) {
          usersMap.remove(userListKey)
          logger
            .atWarning()
            .log("%s login denied: Address already logged in as %s", user, u2.userData.name)
          throw ClientAddressException(
            getString("KailleraServerImpl.LoginDeniedAlreadyLoggedInAs", u2.userData.name)
          )
        }
      }
    }

    // passed all checks
    userImpl.accessLevel = access
    userImpl.status = UserStatus.IDLE
    userImpl.loggedIn = true
    usersMap[userListKey] = userImpl
    userImpl.addEvent(ConnectedEvent(this, user))
    delay(20.milliseconds)
    for (loginMessage in loginMessages) {
      userImpl.addEvent(InfoMessageEvent(user, loginMessage))
      delay(20.milliseconds)
    }
    if (access > AccessManager.ACCESS_NORMAL)
      logger.atInfo().log("%s logged in successfully with %s access!", user, ACCESS_NAMES[access])
    else logger.atInfo().log("%s logged in successfully", user)

    // this is fairly ugly
    if (user.isEmuLinkerClient) {
      userImpl.addEvent(InfoMessageEvent(user, ":ACCESS=" + userImpl.accessStr))
      if (access >= AccessManager.ACCESS_SUPERADMIN) {
        var sb = StringBuilder()
        sb.append(":USERINFO=")
        var sbCount = 0
        for (u3 in users) {
          if (!u3.loggedIn) continue
          sb.append(u3.userData.id)
          sb.append(0x02.toChar())
          sb.append(u3.connectSocketAddress.address.hostAddress)
          sb.append(0x02.toChar())
          sb.append(u3.accessStr)
          sb.append(0x02.toChar())
          // str = u3.getName().replace(',','.');
          // str = str.replace(';','.');
          sb.append(u3.userData.name)
          sb.append(0x02.toChar())
          sb.append(u3.ping)
          sb.append(0x02.toChar())
          sb.append(u3.status)
          sb.append(0x02.toChar())
          sb.append(u3.connectionType.byteValue.toInt())
          sb.append(0x03.toChar())
          sbCount++
          if (sb.length > 300) {
            user.addEvent(InfoMessageEvent(user, sb.toString()))
            sb = StringBuilder()
            sb.append(":USERINFO=")
            sbCount = 0
            delay(100.milliseconds)
          }
        }
        if (sbCount > 0) {
          user.addEvent(InfoMessageEvent(user, sb.toString()))
        }
        delay(100.milliseconds)
      }
    }
    delay(20.milliseconds)
    if (access >= AccessManager.ACCESS_ADMIN)
      userImpl.addEvent(InfoMessageEvent(user, getString("KailleraServerImpl.AdminWelcomeMessage")))
    delay(20.milliseconds)
    // TODO(nue): Localize this welcome message?
    // userImpl.addEvent(
    //     new InfoMessageEvent(
    //         user,
    //         getReleaseInfo().getProductName()
    //             + " v"
    //             + getReleaseInfo().getVersionString()
    //             + ": "
    //             + getReleaseInfo().getReleaseDate()
    //             + " - Visit: www.EmuLinker.org"));
    delay(20.milliseconds)
    addEvent(UserJoinedEvent(this, user))
    delay(20.milliseconds)
    val announcement = accessManager.getAnnouncement(user.socketAddress.address)
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
  override fun quit(user: KailleraUser, message: String?) {
    lookingForGameReporter.cancelActionsForUser(user.userData.id)
    if (!user.loggedIn) {
      usersMap.remove(user.userData.id)
      logger.atSevere().log("%s quit failed: Not logged in", user)
      throw QuitException(getString("KailleraServerImpl.NotLoggedIn"))
    }
    if (usersMap.remove(user.userData.id) == null)
      logger.atSevere().log("%s quit failed: not in user list", user)
    val userGame = (user as KailleraUserImpl).game
    if (userGame != null) user.quitGame()
    var quitMsg = message!!.trim { it <= ' ' }
    if (
      quitMsg.isBlank() ||
        (flags.maxQuitMessageLength > 0 && quitMsg.length > flags.maxQuitMessageLength)
    ) {
      quitMsg = getString("KailleraServerImpl.StandardQuitMessage")
    }
    val access = user.server.accessManager.getAccess(user.socketAddress.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        user.server.accessManager.isSilenced(user.socketAddress.address)
    ) {
      quitMsg = "www.EmuLinker.org"
    }
    logger.atInfo().log("%s quit: %s", user, quitMsg)
    val quitEvent = UserQuitEvent(this, user, quitMsg)
    addEvent(quitEvent)
    user.addEvent(quitEvent)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  override fun chat(user: KailleraUser, message: String) {
    var message = message
    if (!user.loggedIn) {
      logger.atSevere().log("%s chat failed: Not logged in", user)
      throw ChatException(getString("KailleraServerImpl.NotLoggedIn"))
    }
    val access = accessManager.getAccess(user.socketAddress.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(user.socketAddress.address)
    ) {
      logger.atWarning().log("%s chat denied: Silenced: %s", user, message)
      throw ChatException(getString("KailleraServerImpl.ChatDeniedSilenced"))
    }
    if (
      access == AccessManager.ACCESS_NORMAL &&
        flags.chatFloodTime > 0 &&
        (System.currentTimeMillis() - (user as KailleraUserImpl).lastChatTime <
          flags.chatFloodTime * 1000)
    ) {
      logger.atWarning().log("%s chat denied: Flood: %s", user, message)
      throw FloodException(getString("KailleraServerImpl.ChatDeniedFloodControl"))
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
          logger.atWarning().log("%s chat denied: Illegal characters in message", user)
          throw ChatException(getString("KailleraServerImpl.ChatDeniedIllegalCharacters"))
        }
      }
      if (flags.maxChatLength > 0 && message.length > flags.maxChatLength) {
        logger.atWarning().log("%s chat denied: Message Length > %d", user, flags.maxChatLength)
        throw ChatException(getString("KailleraServerImpl.ChatDeniedMessageTooLong"))
      }
    }
    logger.atInfo().log("%s chat: %s", user, message)
    addEvent(ChatEvent(this, user, message))
    if (switchTrivia) {
      if (!trivia!!.isAnswered && trivia!!.isCorrect(message)) {
        trivia!!.addScore(user.userData.name, user.socketAddress.address.hostAddress, message)
      }
    }
  }

  @Throws(CreateGameException::class, FloodException::class)
  override suspend fun createGame(user: KailleraUser, romName: String): KailleraGame {
    if (!user.loggedIn) {
      logger.atSevere().log("%s create game failed: Not logged in", user)
      throw CreateGameException(getString("KailleraServerImpl.NotLoggedIn"))
    }
    if ((user as KailleraUserImpl).game != null) {
      logger.atSevere().log("%s create game failed: already in game: %s", user, user.game)
      throw CreateGameException(getString("KailleraServerImpl.CreateGameErrorAlreadyInGame"))
    }
    if (
      flags.maxGameNameLength > 0 && romName.trim { it <= ' ' }.length > flags.maxGameNameLength
    ) {
      logger
        .atWarning()
        .log("%s create game denied: Rom Name Length > %d", user, flags.maxGameNameLength)
      throw CreateGameException(getString("KailleraServerImpl.CreateGameDeniedNameTooLong"))
    }
    if (romName.lowercase(Locale.getDefault()).contains("|")) {
      logger.atWarning().log("%s create game denied: Illegal characters in ROM name", user)
      throw CreateGameException(getString("KailleraServerImpl.CreateGameDeniedIllegalCharacters"))
    }
    val access = accessManager.getAccess(user.socketAddress.address)
    if (access == AccessManager.ACCESS_NORMAL) {
      if (
        flags.createGameFloodTime > 0 &&
          System.currentTimeMillis() - user.lastCreateGameTime < flags.createGameFloodTime * 1000
      ) {
        logger.atWarning().log("%s create game denied: Flood: %s", user, romName)
        throw FloodException(getString("KailleraServerImpl.CreateGameDeniedFloodControl"))
      }
      if (flags.maxGames > 0 && games.size >= flags.maxGames) {
        logger
          .atWarning()
          .log("%s create game denied: Over maximum of %d current games!", user, flags.maxGames)
        throw CreateGameException(
          getString("KailleraServerImpl.CreateGameDeniedMaxGames", flags.maxGames)
        )
      }
      val chars = romName.toCharArray()
      for (i in chars.indices) {
        if (chars[i].code < 32) {
          logger.atWarning().log("%s create game denied: Illegal characters in ROM name", user)
          throw CreateGameException(
            getString("KailleraServerImpl.CreateGameDeniedIllegalCharacters")
          )
        }
      }
      if (romName.trim { it <= ' ' }.isEmpty()) {
        logger.atWarning().log("%s create game denied: Rom Name Empty", user)
        throw CreateGameException(getString("KailleraServerImpl.CreateGameErrorEmptyName"))
      }
      if (!accessManager.isGameAllowed(romName)) {
        logger
          .atWarning()
          .log("%s create game denied: AccessManager denied game: %s", user, romName)
        throw CreateGameException(getString("KailleraServerImpl.CreateGameDeniedGameBanned"))
      }
    }
    val gameID = getNextGameID()
    val game = KailleraGameImpl(gameID, romName, user, this, flags.gameBufferSize)
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
      getString("KailleraServerImpl.UserCreatedGameAnnouncement", user.userData.name, game.romName),
      false,
    )
    if (
      lookingForGameReporter.reportAndStartTimer(LookingForGameEvent(game.id, game.romName, user))
    ) {
      user.game!!.announce(
        getString(
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
      throw CloseGameException(getString("KailleraServerImpl.NotLoggedIn"))
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

  override fun checkMe(user: KailleraUser, message: String): Boolean {
    var message = message
    if (!user.loggedIn) {
      logger.atSevere().log("%s chat failed: Not logged in", user)
      return false
    }
    val access = accessManager.getAccess(user.socketAddress.address)
    if (
      access < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(user.socketAddress.address)
    ) {
      logger.atWarning().log("%s /me: Silenced: %s", user, message)
      return false
    }

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

  override fun announce(message: String, gamesAlso: Boolean) {
    announce(message, gamesAlso, targetUser = null)
  }

  override fun announce(message: String, gamesAlso: Boolean, targetUser: KailleraUserImpl?) {
    if (targetUser == null) {
      users
        .asSequence()
        .filter { it.loggedIn }
        .forEach { kailleraUser ->
          kailleraUser.addEvent(InfoMessageEvent(kailleraUser, message))

          if (gamesAlso && kailleraUser.game != null) {
            kailleraUser.game!!.announce(message, kailleraUser)
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

  fun addEvent(event: KailleraEvent) {
    for (user in usersMap.values) {
      if (user.loggedIn) {
        if (user.status != UserStatus.IDLE) {
          if (user.ignoringUnnecessaryServerActivity) {
            when (event) {
              is GameDataEvent -> user.addEvent(event)
              is ChatEvent -> continue
              is UserJoinedEvent -> continue
              is UserQuitEvent -> continue
              is GameStatusChangedEvent -> continue
              is GameClosedEvent -> continue
              is GameCreatedEvent -> continue
              else -> user.addEvent(event)
            }
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

  override suspend fun run(globalContext: CoroutineContext) {
    threadIsActive = true
    logger.atFine().log("KailleraServer thread running...")
    try {
      while (!stopFlag) {
        // TODO(nue): Can we remove this try/catch? I don't know if InterruptedException gets
        // thrown.
        try {
          delay((flags.maxPing * 3).milliseconds)
        } catch (e: InterruptedException) {
          logger.atSevere().withCause(e).log("Sleep Interrupted!")
        }

        if (stopFlag) break
        if (usersMap.isEmpty()) continue
        for (user in users) {

          //          TODO(nue): Is this necessary?
          // user.mutex.withLock {
          val access = accessManager.getAccess(user.connectSocketAddress.address)
          user.accessLevel = access

          // LagStat
          if (user.loggedIn) {
            val game = user.game
            if (game != null && game.status == GameStatus.PLAYING && !game.startTimeout) {
              if (System.currentTimeMillis() - game.startTimeoutTime > 15000) {
                game.startTimeout = true
              }
            }
          }
          if (
            !user.loggedIn &&
              Instant.now().toEpochMilli() - user.connectTime.toEpochMilli() > flags.maxPing * 15
          ) {
            logger.atInfo().log("%s connection timeout!", user)
            user.stop()
            usersMap.remove(user.userData.id)
          } else if (
            user.loggedIn &&
              Instant.now().toEpochMilli() - user.lastKeepAlive.toEpochMilli() >
                flags.keepAliveTimeout.inWholeMilliseconds
          ) {
            logger.atInfo().log("%s keepalive timeout!", user)
            try {
              quit(user, getString("KailleraServerImpl.ForcedQuitPingTimeout"))
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
              (Instant.now().toEpochMilli() - user.lastActivity.toEpochMilli() >
                flags.idleTimeout.inWholeMilliseconds)
          ) {
            logger.atInfo().log("%s inactivity timeout!", user)
            try {
              quit(user, getString("KailleraServerImpl.ForcedQuitInactivityTimeout"))
            } catch (e: Exception) {
              logger
                .atSevere()
                .withCause(e)
                .log("Error forcing %s quit for inactivity timeout!", user)
            }
          } else if (user.loggedIn && access < AccessManager.ACCESS_NORMAL) {
            logger.atInfo().log("%s banned!", user)
            try {
              quit(user, getString("KailleraServerImpl.ForcedQuitBanned"))
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
              quit(user, getString("KailleraServerImpl.ForcedQuitEmulatorRestricted"))
            } catch (e: Exception) {
              logger
                .atSevere()
                .withCause(e)
                .log("Error forcing %s quit because emulator restricted!", user)
            }
          } else {}
          // End of user.mutex.withLock {
        }
      }
    } catch (e: Throwable) {
      if (!stopFlag) {
        logger.atSevere().withCause(e).log("KailleraServer thread caught unexpected exception")
      }
    } finally {
      threadIsActive = false
      logger.atFine().log("KailleraServer thread exiting...")
    }
  }

  init {
    val loginMessagesBuilder = mutableListOf<String>()
    var i = 1
    while (hasString("KailleraServerImpl.LoginMessage.$i")) {
      loginMessagesBuilder.add(getString("KailleraServerImpl.LoginMessage.$i"))
      i++
    }
    loginMessages = loginMessagesBuilder.toList()
    flags.connectionTypes.forEach {
      val ct = it.toInt()
      allowedConnectionTypes[ct] = true
    }
    if (flags.touchKaillera) {
      this.statsCollector = statsCollector
    }

    if (flags.metricsEnabled) {
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
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
