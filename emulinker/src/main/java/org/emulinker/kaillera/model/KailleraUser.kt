package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.StopFlagEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.*
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.Executable

class KailleraUser(
  var userData: UserData,
  val protocol: String,
  val connectSocketAddress: InetSocketAddress,
  val listener: V086ClientHandler,
  val server: KailleraServer,
  flags: RuntimeFlags,
) : Executable {

  /** [CoroutineScope] for long-running actions attached to the user. */
  val userCoroutineScope =
    CoroutineScope(Dispatchers.IO) + CoroutineName("User[${userData.id}]Scope")

  var inStealthMode = false

  /** Example: "Project 64k 0.13 (01 Aug 2003)" */
  var clientType: String? = null
    set(clientType) {
      field = clientType
      if (clientType != null && clientType.startsWith(EMULINKER_CLIENT_NAME)) {
        isEmuLinkerClient = true
      }
    }

  private val initTime: Instant = Instant.now()

  var connectionType: ConnectionType =
    ConnectionType.DISABLED // TODO(nue): This probably shouldn't have a default.
  var ping = 0
  lateinit var socketAddress: InetSocketAddress
  var status = UserStatus.PLAYING // TODO(nue): This probably shouldn't have a default value..

  /**
   * Level of access that the user has.
   *
   * See AdminCommandAction for available values. This should be turned into an enum.
   */
  var accessLevel = 0
  var isEmuLinkerClient = false
    private set
  val connectTime = initTime
  var timeouts = 0
  var lastActivity = initTime
    private set

  var smallLagSpikesCausedByUser = 0L
  var bigLagSpikesCausedByUser = 0L

  /** The last time we heard from this player for lag detection purposes. */
  private var lastUpdate = Instant.now()
  private var smallLagThreshold = Duration.ZERO
  private var bigSpikeThreshold = Duration.ZERO

  // Saved to a variable because I think this might give a speed boost.
  private val improvedLagstat = flags.improvedLagstatEnabled

  fun updateLastActivity() {
    lastKeepAlive = Instant.now()
    lastActivity = lastKeepAlive
  }

  var lastKeepAlive = initTime
    private set
  var lastChatTime: Long = initTime.toEpochMilli()
    private set
  var lastCreateGameTime: Long = 0
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

  var playerNumber = -1 // TODO(nue): Make this nullable.
  var ignoreAll = false
  var isAcceptingDirectMessages = true
  var lastMsgID = -1
  var isMuted = false

  private val eventChannel = Channel<KailleraEvent>(10)

  private val lostInput: MutableList<ByteArray> = ArrayList()
  /** Note that this is a different type from lostInput. */
  fun getLostInput(): ByteArray {
    return lostInput[0]
  }

  private val ignoredUsers: MutableList<String> = ArrayList()
  private var gameDataErrorTime: Long = -1

  // TODO(nue): Get rid of this.
  @Deprecated(message = "Isn't needed", level = DeprecationLevel.ERROR)
  override var threadIsActive = false
    private set

  var tempDelay = 0

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

  fun searchIgnoredUsers(address: String): Boolean = ignoredUsers.any { it == address }

  var loggedIn = false

  override fun toString() =
    "User${userData.id}(${if (userData.name.length > 15) userData.name.take(15) + "..." else userData.name}/${connectSocketAddress.address.hostAddress})"

  fun updateLastKeepAlive() {
    lastKeepAlive = Instant.now()
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

  override fun equals(other: Any?) = other is KailleraUser && other.userData.id == userData.id

  fun toDetailedString(): String {
    return ("KailleraUser[id=${userData.id} protocol=$protocol status=$status name=${userData.name} clientType=$clientType ping=$ping connectionType=$connectionType remoteAddress=" +
      (if (!this::socketAddress.isInitialized) {
        EmuUtil.formatSocketAddress(connectSocketAddress)
      } else EmuUtil.formatSocketAddress(socketAddress)) +
      "]")
  }

  override suspend fun stop() {
    logger.atFine().log("Stopping KaillerUser for %d", userData.id)
    delay(500.milliseconds)
    addEvent(StopFlagEvent())
    listener.stop()
    eventChannel.close()
    userCoroutineScope.cancel("Stopping KailleraUser $userData")
  }

  @Synchronized
  fun droppedPacket() {
    game?.droppedPacket(this)
  }

  // server actions
  @Throws(
    PingTimeException::class,
    ClientAddressException::class,
    ConnectionTypeException::class,
    UserNameException::class,
    LoginException::class
  )
  suspend fun login() {
    updateLastActivity()
    server.login(this)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  fun chat(message: String) {
    updateLastActivity()
    server.chat(this, message)
    lastChatTime = System.currentTimeMillis()
  }

  @Synchronized
  @Throws(GameKickException::class)
  fun gameKick(userID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s kick User %d failed: Not in a game", this, userID)
      throw GameKickException(EmuLang.getString("KailleraUser.KickErrorNotInGame"))
    }
    game?.kick(this, userID)
  }

  @Throws(CreateGameException::class, FloodException::class)
  suspend fun createGame(romName: String): KailleraGame {
    updateLastActivity()
    requireNotNull(server.getUser(userData.id)) { "$this create game failed: User don't exist!" }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("%s create game failed: User status is Playing!", this)
      throw CreateGameException(EmuLang.getString("KailleraUser.CreateGameErrorAlreadyInGame"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("%s create game failed: User status is Connecting!", this)
      throw CreateGameException(EmuLang.getString("KailleraUser.CreateGameErrorNotFullyConnected"))
    }
    val game = server.createGame(this, romName)
    lastCreateGameTime = System.currentTimeMillis()
    return game
  }

  @Synchronized
  @Throws(
    QuitException::class,
    DropGameException::class,
    QuitGameException::class,
    CloseGameException::class
  )
  fun quit(message: String?) {
    updateLastActivity()
    server.quit(this, message)
    loggedIn = false
  }

  @Throws(JoinGameException::class)
  suspend fun joinGame(gameID: Int): KailleraGame {
    updateLastActivity()
    if (game != null) {
      logger.atWarning().log("%s join game failed: Already in: %s", this, game)
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorAlreadyInGame"))
    }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("%s join game failed: User status is Playing!", this)
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorAnotherGameRunning"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("%s join game failed: User status is Connecting!", this)
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorNotFullConnected"))
    }
    val game = server.getGame(gameID)
    if (game == null) {
      logger.atWarning().log("%s join game failed: Game %d does not exist!", this, gameID)
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorDoesNotExist"))
    }

    // if (connectionType != game.getOwner().getConnectionType())
    // {
    //	logger.atWarning().log(this + " join game denied: " + this + ": You must use the same
    // connection type as
    // the owner: " + game.getOwner().getConnectionType());
    //	throw new
    // JoinGameException(EmuLang.getString("KailleraGame.StartGameConnectionTypeMismatchInfo"));
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
    if (game == null) {
      logger.atWarning().log("%s game chat failed: Not in a game", this)
      throw GameChatException(EmuLang.getString("KailleraUser.GameChatErrorNotInGame"))
    }
    if (isMuted) {
      logger.atWarning().log("%s gamechat denied: Muted: %s", this, message)
      game!!.announce("You are currently muted!", this)
      return
    }
    if (server.accessManager.isSilenced(socketAddress.address)) {
      logger.atWarning().log("%s gamechat denied: Silenced: %s", this, message)
      game!!.announce("You are currently silenced!", this)
      return
    }
    game!!.chat(this, message)
  }

  @Synchronized
  @Throws(DropGameException::class)
  fun dropGame() {
    updateLastActivity()
    if (status == UserStatus.IDLE) {
      return
    }
    status = UserStatus.IDLE
    if (game != null) {
      game!!.drop(this, playerNumber)
      // not necessary to show it twice
      /*if(p2P == true)
      	game.announce("Please Relogin, to update your client of missed server activity during P2P!", this);
      p2P = false;*/
    } else logger.atFine().log("%s drop game failed: Not in a game", this)
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
    game!!.quit(this, playerNumber)
    if (status != UserStatus.IDLE) {
      status = UserStatus.IDLE
    }
    isMuted = false
    game = null
    addEvent(UserQuitGameEvent(game = null, this))
  }

  @Synchronized
  @Throws(StartGameException::class)
  fun startGame() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s start game failed: Not in a game", this)
      throw StartGameException(EmuLang.getString("KailleraUser.StartGameErrorNotInGame"))
    }
    game!!.start(this)
  }

  @Synchronized
  @Throws(UserReadyException::class)
  fun playerReady() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s player ready failed: Not in a game", this)
      throw UserReadyException(EmuLang.getString("KailleraUser.PlayerReadyErrorNotInGame"))
    }
    if (
      playerNumber > game!!.playerActionQueue!!.size ||
        game!!.playerActionQueue!![playerNumber - 1].synched
    ) {
      return
    }
    totalDelay = game!!.highestUserFrameDelay + tempDelay + 5

    smallLagThreshold =
      Duration.ofSeconds(1)
        .dividedBy(connectionType.updatesPerSecond.toLong())
        .multipliedBy(frameDelay.toLong())
        // Effectively this is the delay that is allowed before calling it a lag spike.
        .plusMillis(10)
    bigSpikeThreshold =
      Duration.ofSeconds(1)
        .dividedBy(connectionType.updatesPerSecond.toLong())
        .multipliedBy(frameDelay.toLong())
        .plusMillis(50)
    game!!.ready(this, playerNumber)
  }

  @Throws(GameDataException::class)
  fun addGameData(data: ByteArray) {
    if (improvedLagstat) {
      val delaySinceLastResponse = Duration.between(lastUpdate, Instant.now())
      if (delaySinceLastResponse.nano in smallLagThreshold.nano..bigSpikeThreshold.nano) {
        smallLagSpikesCausedByUser++
      } else if (delaySinceLastResponse.nano > bigSpikeThreshold.nano) {
        bigLagSpikesCausedByUser++
        // TODO(nue): Add some metric for laggy games/users and clean up this code.
        if (bigLagSpikesCausedByUser == 50L) {
          logger.atWarning().log("VERY LAGGY GAME! %s", this)
        }
      }
    }

    updateLastActivity()
    try {
      if (game == null) {
        throw GameDataException(
          EmuLang.getString("KailleraUser.GameDataErrorNotInGame"),
          data,
          connectionType.byteValue.toInt(),
          playerNumber = 1,
          numPlayers = 1
        )
      }

      // Initial Delay
      // totalDelay = (game.getDelay() + tempDelay + 5)
      if (frameCount < totalDelay) {
        bytesPerAction = data.size / connectionType.byteValue
        arraySize = game!!.playerActionQueue!!.size * connectionType.byteValue * bytesPerAction
        val response = ByteArray(arraySize)
        for (i in response.indices) {
          response[i] = 0
        }
        lostInput.add(data)
        addEvent(GameDataEvent(game!!, response))
        frameCount++
      } else {
        // lostInput.add(data);
        if (lostInput.size > 0) {
          game!!.addData(this, playerNumber, lostInput[0])
          lostInput.removeAt(0)
        } else {
          game!!.addData(this, playerNumber, data)
        }
      }
      gameDataErrorTime = 0
    } catch (e: GameDataException) {
      // TODO(nue): Investigate this comment:
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
          throw GameDataException(e.message)
        } else {
          // e.setReflectData(true);
          throw e
        }
      } else {
        gameDataErrorTime = System.currentTimeMillis()
        throw e
      }
    }

    if (improvedLagstat) {
      lastUpdate = Instant.now()
    }
  }

  fun addEvent(event: KailleraEvent) {
    if (status != UserStatus.IDLE) {
      if (ignoringUnnecessaryServerActivity) {
        if (event.toString() == "InfoMessageEvent") return
      }
    }
    // TODO(nue): This method should be marked as suspend instead.
    runBlocking { eventChannel.send(event) }
  }

  suspend fun handleEvent(event: KailleraEvent) {
    if (event is StopFlagEvent) {
      return
    }
    listener.handleKailleraEvent(event)
    when {
      event is GameStartedEvent -> {
        status = UserStatus.PLAYING
        if (improvedLagstat) {
          lastUpdate = Instant.now()
        }
      }
      event is UserQuitEvent && event.user == this -> {
        stop()
      }
    }
  }

  override suspend fun run(globalContext: CoroutineContext) {
    // Run over all events as they come in.
    for (event in eventChannel) {
      handleEvent(event)
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val EMULINKER_CLIENT_NAME = "EmulinkerSF Admin Client"
  }
}
