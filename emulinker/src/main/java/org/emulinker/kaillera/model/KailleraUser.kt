package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.InfoMessageEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.ChatException
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.CreateGameException
import org.emulinker.kaillera.model.exception.DropGameException
import org.emulinker.kaillera.model.exception.FloodException
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.exception.GameDataException
import org.emulinker.kaillera.model.exception.GameKickException
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.QuitException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.exception.UserReadyException
import org.emulinker.util.CircularVariableSizeByteArrayBuffer
import org.emulinker.util.EmuLang
import org.emulinker.util.TimeOffsetCache
import org.emulinker.util.VariableSizeByteArray

/**
 * Represents a user in the server.
 *
 * A thread is dedicated to each user and we can probably clean this up.
 */
class KailleraUser(
  val id: Int,
  val protocol: String,
  val connectSocketAddress: InetSocketAddress,
  private val clientHandler: V086ClientHandler,
  val server: KailleraServer,
  private val flags: RuntimeFlags,
  private val clock: Clock,
) {
  var inStealthMode = false

  /** Example: "Project 64k 0.13 (01 Aug 2003)" */
  var clientType: String? = null
    set(clientType) {
      field = clientType
      if (clientType != null && clientType.startsWith(EMULINKERSF_ADMIN_CLIENT_NAME))
        isEsfAdminClient = true
    }

  private val initTime = clock.now()
  val connectTime: Instant = initTime

  var connectionType: ConnectionType =
    ConnectionType.DISABLED // TODO(nue): This probably shouldn't have a default.
  var ping = 0.milliseconds // TODO(nue): This probably shouldn't have a default.
  var socketAddress: InetSocketAddress? = null
  var status = UserStatus.PLAYING // TODO(nue): This probably shouldn't have a default value..

  /** A non-threadsafe cache of [VariableSizeByteArray] instances. */
  val circularVariableSizeByteArrayBuffer =
    CircularVariableSizeByteArrayBuffer(
      // The GameDataCache has 256 so we should have something significantly larger.
      capacity = 2_000
    ) {
      VariableSizeByteArray()
    }

  /**
   * Level of access that the user has.
   *
   * See [AccessManager] for available values. This should be turned into an enum.
   */
  var accessLevel = 0

  var isEsfAdminClient = false
    private set

  /** This marks the last time the user interacted in the server. */
  private var lastActivity: Instant = initTime

  private var lagLeewayNs = 0.seconds.inWholeNanoseconds
  private var totalDriftNs = 0.seconds.inWholeNanoseconds
  private val totalDriftCache =
    TimeOffsetCache(delay = flags.lagstatDuration, resolution = 5.seconds)

  /** Time we received the latest game data from the user for lag measurement purposes. */
  var receivedGameDataNs: Long? = null
    private set

  /** The last time we heard from this player for lag detection purposes. */
  private var lastUpdateNs = System.nanoTime()

  private fun updateLastActivity() {
    lastKeepAlive = clock.now()
    lastActivity = lastKeepAlive
  }

  /**
   * We haven't heard anything from the user in a long time and it's likely their client is no
   * longer connected.
   */
  val isDead: Boolean
    get() =
      clock.now() - lastKeepAlive > flags.keepAliveTimeout &&
        System.nanoTime() - lastUpdateNs > flags.keepAliveTimeout.inWholeNanoseconds

  /**
   * The user may have a successful connection to the server, but they are seemingly AFK for longer
   * than the policy allows.
   */
  val isIdleForTooLong: Boolean
    get() =
      clock.now() - lastActivity > flags.idleTimeout &&
        System.nanoTime() - lastUpdateNs > flags.idleTimeout.inWholeNanoseconds

  private var lastKeepAlive: Instant = initTime
  var lastChatTime: Instant = initTime
    private set

  var lastCreateGameTime: Instant = initTime
    private set

  var frameCount = 0
  var frameDelay = 0

  private var totalDelay = 0
  var bytesPerAction = 0
    private set

  /** User action data response message size (in number of bytes). */
  var arraySize = 0
    private set

  /**
   * This is called "p2p mode" in the code and commands.
   *
   * See the command /p2pon.
   */
  var ignoringUnnecessaryServerActivity = false

  var playerNumber = -1
  var ignoreAll = false
  var isAcceptingDirectMessages = true
  var lastMsgID = -1
  var isMuted = false

  private val lostInput: MutableList<VariableSizeByteArray> = ArrayList()

  /** Note that this is a different type from lostInput. */
  fun getLostInput(): VariableSizeByteArray {
    return lostInput[0]
  }

  private val ignoredUsers: MutableList<String> = ArrayList()
  private var gameDataErrorTime: Long = -1

  private var stopFlag = false

  var tempDelay = 0

  val users: Collection<KailleraUser>
    get() = server.usersMap.values

  fun addIgnoredUser(address: String) {
    ignoredUsers.add(address)
  }

  fun findIgnoredUser(address: String): Boolean {
    return ignoredUsers.any { it == address }
  }

  fun removeIgnoredUser(address: String, removeAll: Boolean): Boolean {
    var here = false
    if (removeAll) {
      ignoredUsers.clear()
      return true
    }
    var i = 0
    while (i < ignoredUsers.size) {
      if (ignoredUsers[i] == address) {
        ignoredUsers.removeAt(i)
        here = true
      }
      i++
    }
    return here
  }

  fun searchIgnoredUsers(address: String): Boolean {
    return ignoredUsers.any { it == address }
  }

  var loggedIn = false

  override fun toString(): String = "User[id=$id name=$name]"

  var name: String? = null

  fun updateLastKeepAlive() {
    lastKeepAlive = clock.now()
  }

  var game: KailleraGame? = null
    set(value) {
      if (value == null) {
        playerNumber = -1
      }
      field = value
    }

  val accessStr: String
    get() = AccessManager.ACCESS_NAMES[accessLevel]

  fun stop() {
    if (stopFlag) {
      logger.atFine().log("%s  thread stop request ignored: already stopping!", this)
      return
    }
    stopFlag = true
    clientHandler.stop()
  }

  fun droppedPacket() {
    if (game != null) {
      // if(game.getStatus() == KailleraGame.STATUS_PLAYING){
      game!!.droppedPacket(this)
      // }
    }
  }

  // server actions
  @Synchronized
  fun login(): Result<Unit> {
    updateLastActivity()
    return server.login(this)
  }

  @Throws(ChatException::class, FloodException::class)
  fun chat(message: String) {
    updateLastActivity()
    server.chat(to = this, message)
    lastChatTime = clock.now()
  }

  @Throws(GameKickException::class)
  fun gameKick(userID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s kick User %d failed: Not in a game", this, userID)
      throw GameKickException(EmuLang.getString("KailleraUserImpl.KickErrorNotInGame"))
    }
    game!!.kick(this, userID)
  }

  @Throws(CreateGameException::class, FloodException::class)
  fun createGame(romName: String): KailleraGame? {
    updateLastActivity()
    if (server.getUser(id) == null) {
      logger.atSevere().log("%s create game failed: User don't exist!", this)
      return null
    }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("%s create game failed: User status is Playing!", this)
      throw CreateGameException(EmuLang.getString("KailleraUserImpl.CreateGameErrorAlreadyInGame"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("%s create game failed: User status is Connecting!", this)
      throw CreateGameException(
        EmuLang.getString("KailleraUserImpl.CreateGameErrorNotFullyConnected")
      )
    }
    val game = server.createGame(this, romName)
    lastCreateGameTime = clock.now()
    return game
  }

  @Synchronized
  @Throws(
    QuitException::class,
    DropGameException::class,
    QuitGameException::class,
    CloseGameException::class,
  )
  fun quit(message: String) {
    updateLastActivity()
    server.quit(this, message)
    loggedIn = false
  }

  fun lagAttributedToUser(): Duration =
    (totalDriftNs - (totalDriftCache.getDelayedValue() ?: 0)).nanoseconds.absoluteValue

  fun resetLag() {
    totalDriftNs = 0
    totalDriftCache.clear()
  }

  @Synchronized
  @Throws(JoinGameException::class)
  fun joinGame(gameID: Int): KailleraGame {
    updateLastActivity()
    if (game != null) {
      logger.atWarning().log("%s join game failed: Already in: %s", this, game)
      throw JoinGameException(EmuLang.getString("KailleraUserImpl.JoinGameErrorAlreadyInGame"))
    }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("%s join game failed: User status is Playing!", this)
      throw JoinGameException(EmuLang.getString("KailleraUserImpl.JoinGameErrorAnotherGameRunning"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("%s join game failed: User status is Connecting!", this)
      throw JoinGameException(EmuLang.getString("KailleraUserImpl.JoinGameErrorNotFullConnected"))
    }
    val game = server.getGame(gameID)
    if (game == null) {
      logger.atWarning().log("%s join game failed: Game %d does not exist!", this, gameID)
      throw JoinGameException(EmuLang.getString("KailleraUserImpl.JoinGameErrorDoesNotExist"))
    }

    // if (connectionType != game.getOwner().getConnectionType())
    // {
    //	logger.atWarning().log(this + " join game denied: " + this + ": You must use the same
    // connection type as
    // the owner: " + game.getOwner().getConnectionType());
    //	throw new
    // JoinGameException(EmuLang.getString("KailleraGameImpl.StartGameConnectionTypeMismatchInfo"));
    //
    // }
    playerNumber = game.join(this)
    this.game = game
    gameDataErrorTime = -1
    return game
  }

  // game actions
  @Synchronized
  @Throws(GameChatException::class)
  fun gameChat(message: String, messageID: Int) {
    updateLastActivity()
    val game = this.game
    if (game == null) {
      logger.atWarning().log("%s game chat failed: Not in a game", this)
      throw GameChatException(EmuLang.getString("KailleraUserImpl.GameChatErrorNotInGame"))
    }
    if (isMuted) {
      logger.atWarning().log("%s gamechat denied: Muted: %s", this, message)
      game.announce("You are currently muted!", this)
      return
    }
    if (server.accessManager.isSilenced(socketAddress!!.address)) {
      logger.atWarning().log("%s gamechat denied: Silenced: %s", this, message)
      game.announce("You are currently silenced!", this)
      return
    }

    game.chat(this, message)
  }

  @Synchronized
  @Throws(DropGameException::class)
  fun dropGame() {
    updateLastActivity()
    if (status == UserStatus.IDLE) {
      return
    }
    status = UserStatus.IDLE
    val game = this.game
    if (game != null) {
      game.drop(this, playerNumber)
      // not necessary to show it twice
      /*if(p2P == true)
      	game.announce("Please Relogin, to update your client of missed server activity during P2P!", this);
      p2P = false;*/
    } else {
      logger.atFine().log("%s drop game failed: Not in a game", this)
    }
  }

  @Synchronized
  @Throws(DropGameException::class, QuitGameException::class, CloseGameException::class)
  fun quitGame() {
    updateLastActivity()
    if (game == null) {
      logger.atFine().log("%s quit game failed: Not in a game", this)
      // throw new QuitGameException("You are not in a game!");
      return
    }
    if (status == UserStatus.PLAYING) {
      // first set STATUS_IDLE and then call game.drop, otherwise if someone
      // quit game whitout drop - game status will not change to STATUS_WAITING
      status = UserStatus.IDLE
      game!!.drop(this, playerNumber)
    }
    game?.quit(this, playerNumber)
    if (status != UserStatus.IDLE) {
      status = UserStatus.IDLE
    }
    isMuted = false
    game = null
    queueEvent(UserQuitGameEvent(game, this))
  }

  @Synchronized
  @Throws(StartGameException::class)
  fun startGame() {
    resetLag()
    updateLastActivity()
    val game = this.game
    if (game == null) {
      logger.atWarning().log("%s start game failed: Not in a game", this)
      throw StartGameException(EmuLang.getString("KailleraUserImpl.StartGameErrorNotInGame"))
    }
    game.start(this)
  }

  @Synchronized
  @Throws(UserReadyException::class)
  fun playerReady() {
    updateLastActivity()
    val game = this.game
    if (game == null) {
      logger.atWarning().log("%s player ready failed: Not in a game", this)
      throw UserReadyException(EmuLang.getString("KailleraUserImpl.PlayerReadyErrorNotInGame"))
    }
    if (
      playerNumber > game.playerActionQueues.size ||
        game.playerActionQueues[playerNumber - 1].synced
    ) {
      return
    }
    totalDelay = game.highestUserFrameDelay + tempDelay + 5

    game.ready(this, playerNumber)
  }

  // Current source of the lag.
  fun addGameData(data: VariableSizeByteArray): Result<Unit> {
    receivedGameDataNs = System.nanoTime()
    fun doTheThing(): Result<Unit> {
      // Returning success when the game doesn't exist might not be correct?
      val game = this.game ?: return Result.success(Unit)

      // Initial Delay
      // totalDelay = (game.getDelay() + tempDelay + 5)
      if (frameCount < totalDelay) {
        bytesPerAction = data.size / connectionType.byteValue
        arraySize = game.playerActionQueues.size * connectionType.byteValue * bytesPerAction
        lostInput.add(data)
        doEvent(GameDataEvent(game, VariableSizeByteArray(ByteArray(arraySize) { 0 })))
        frameCount++
      } else {
        // lostInput.add(data);
        if (lostInput.isNotEmpty()) {
          when (val r = game.addData(this, playerNumber, lostInput[0])) {
            AddDataResult.Success -> {}
            AddDataResult.IgnoringDesynched -> {}
            is AddDataResult.Failure -> return Result.failure(r.exception)
          }
          lostInput.removeAt(0)
        } else {
          when (val r = game.addData(this, playerNumber, data)) {
            AddDataResult.Success -> {}
            AddDataResult.IgnoringDesynched -> {}
            is AddDataResult.Failure -> return Result.failure(r.exception)
          }
        }
      }
      gameDataErrorTime = 0
      return Result.success(Unit)
    }

    val result = doTheThing()
    result.onFailure { e ->
      when (e) {
        is GameDataException -> {
          // this should be warn level, but it creates tons of lines in the log
          logger.atFine().withCause(e).log("%s add game data failed", this)

          // i'm going to reflect the game data packet back at the user to prevent game lockups,
          // but this uses extra bandwidth, so we'll set a counter to prevent people from leaving
          // games running for a long time in this state
          if (gameDataErrorTime > 0) {
            // give the user time to close the game
            if (System.currentTimeMillis() - gameDataErrorTime > 30000) {
              // this should be warn level, but it creates tons of lines in the log
              logger.atFine().log("%s: error game data exceeds drop timeout!", this)
              return Result.failure(GameDataException(e.message))
            } else {
              // e.setReflectData(true);
              return result
            }
          } else {
            gameDataErrorTime = System.currentTimeMillis()
            return result
          }
        }
        else -> throw e
      }
    }
    return Result.success(Unit)
  }

  fun updateUserDrift() {
    val receivedGameDataNs = receivedGameDataNs ?: return
    val nowNs = System.nanoTime()
    val delaySinceLastResponseNs = nowNs - lastUpdateNs
    val timeWaitingNs = nowNs - receivedGameDataNs
    val delaySinceLastResponseMinusWaitingNs = delaySinceLastResponseNs - timeWaitingNs
    val leewayChangeNs =
      game!!.singleFrameDurationForLagCalculationOnlyNs - delaySinceLastResponseMinusWaitingNs
    lagLeewayNs += leewayChangeNs
    if (lagLeewayNs < 0) {
      // Lag leeway fell below zero. We caused lag!
      totalDriftNs += lagLeewayNs
      lagLeewayNs = 0
    } else if (lagLeewayNs > game!!.singleFrameDurationForLagCalculationOnlyNs) {
      // Does not make sense to allow lag leeway to be longer than the length of one frame.
      lagLeewayNs = game!!.singleFrameDurationForLagCalculationOnlyNs
    }
    lastUpdateNs = nowNs
    totalDriftCache.update(totalDriftNs, nowNs = nowNs)
  }

  fun queueEvent(event: KailleraEvent) {
    server.queueEvent(this, event)
  }

  /** Acts on an event in realtime. */
  fun doEvent(event: KailleraEvent) {
    if (
      status != UserStatus.IDLE && ignoringUnnecessaryServerActivity && event is InfoMessageEvent
    ) {
      return
    }

    clientHandler.actionPerformed(event)
    if (event is GameStartedEvent) {
      status = UserStatus.PLAYING
      lastUpdateNs = System.nanoTime()
    } else if (event is UserQuitEvent && event.user == this) {
      stop()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KailleraUser

    return id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val EMULINKERSF_ADMIN_CLIENT_NAME = "EmulinkerSF Admin Client"
  }
}
