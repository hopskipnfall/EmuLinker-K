package org.emulinker.kaillera.model.impl

import com.google.common.flogger.FluentLogger
import com.google.common.flogger.StackSize
import java.lang.InterruptedException
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import kotlin.Throws
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.UserStatus
import org.emulinker.kaillera.model.event.*
import org.emulinker.kaillera.model.exception.*
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.Executable

private const val EMULINKER_CLIENT_NAME = "EmulinkerSF Admin Client"

class KailleraUserImpl(
    override val id: Int,
    override val protocol: String,
    override val connectSocketAddress: InetSocketAddress,
    override val listener: KailleraEventListener,
    override val server: KailleraServerImpl,
    flags: RuntimeFlags,
) : KailleraUser, Executable {
  /** [CoroutineScope] for long-running actions attached to the user. */
  private val userCoroutineScope =
      CoroutineScope(Dispatchers.IO) + CoroutineName("User[${id}]Scope")

  override var inStealthMode = false

  override val mutex = Mutex()

  /** Example: "Project 64k 0.13 (01 Aug 2003)" */
  override var clientType: String? = null
    set(clientType) {
      field = clientType
      if (clientType != null && clientType.startsWith(EMULINKER_CLIENT_NAME)) {
        isEmuLinkerClient = true
      }
    }

  private val initTime: Instant = Instant.now()

  override var connectionType: ConnectionType =
      ConnectionType.DISABLED // TODO(nue): This probably shouldn't have a default.
  override var ping = 0
  override lateinit var socketAddress: InetSocketAddress
  override var status =
      UserStatus.PLAYING // TODO(nue): This probably shouldn't have a default value..
  override var accessLevel = 0
  override var isEmuLinkerClient = false
    private set
  override val connectTime = initTime
  override var timeouts = 0
  override var lastActivity = initTime
    private set

  override var smallLagSpikesCausedByUser = 0L
  override var bigLagSpikesCausedByUser = 0L

  /** The last time we heard from this player for lag detection purposes. */
  private var lastUpdate = Instant.now()
  private var smallLagThreshold = Duration.ZERO
  private var bigSpikeThreshold = Duration.ZERO

  // Saved to a variable because I think this might give a speed boost.
  private val improvedLagstat = flags.improvedLagstatEnabled

  override fun updateLastActivity() {
    lastKeepAlive = Instant.now()
    lastActivity = lastKeepAlive
  }

  override var lastKeepAlive = initTime
    private set
  var lastChatTime: Long = initTime.toEpochMilli()
    private set
  var lastCreateGameTime: Long = 0
    private set
  override var frameCount = 0
  override var frameDelay = 0

  private var totalDelay = 0
  override var bytesPerAction = 0
    private set

  override var arraySize = 0
    private set

  override var ignoringUnnecessaryServerActivity = false

  override var playerNumber = -1
  override var ignoreAll = false
  override var isAcceptingDirectMessages = true
  override var lastMsgID = -1
  override var isMuted = false

  private val lostInput: MutableList<ByteArray> = ArrayList()
  /** Note that this is a different type from lostInput. */
  override fun getLostInput(): ByteArray {
    return lostInput[0]
  }

  private val ignoredUsers: MutableList<String> = ArrayList()
  private var gameDataErrorTime: Long = -1

  override var threadIsActive = false
    private set

  private var stopFlag = false
  private val eventChannel = Channel<KailleraEvent>(5)

  override var tempDelay = 0

  override val users: Collection<KailleraUserImpl>
    get() = server.users

  override fun addIgnoredUser(address: String) {
    ignoredUsers.add(address)
  }

  override fun findIgnoredUser(address: String): Boolean {
    return ignoredUsers.any { it == address }
  }

  override fun removeIgnoredUser(address: String, removeAll: Boolean): Boolean {
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

  override fun searchIgnoredUsers(address: String): Boolean {
    return ignoredUsers.any { it == address }
  }

  override var loggedIn = false

  override fun toString(): String {
    return if (!this::name.isInitialized) {
      "User$id(${connectSocketAddress.address.hostAddress})"
    } else {
      "User$id(${if (name.length > 15) name.take(15) + "..." else name}/${connectSocketAddress.address.hostAddress})"
    }
  }

  override lateinit var name: String

  override fun updateLastKeepAlive() {
    lastKeepAlive = Instant.now()
  }

  override var game: KailleraGameImpl? = null
    set(value) {
      if (value == null) {
        playerNumber = -1
      }
      field = value
    }

  val accessStr: String
    get() = AccessManager.ACCESS_NAMES[accessLevel]

  override fun equals(other: Any?): Boolean {
    return other is KailleraUserImpl && other.id == id
  }

  fun toDetailedString(): String {
    return ("KailleraUserImpl[id=$id protocol=$protocol status=$status name=$name clientType=$clientType ping=$ping connectionType=$connectionType remoteAddress=" +
        (if (!this::socketAddress.isInitialized) {
          EmuUtil.formatSocketAddress(connectSocketAddress)
        } else EmuUtil.formatSocketAddress(socketAddress)) +
        "]")
  }

  override suspend fun stop() {
    mutex.withLock {
      logger.atFine().log("Stopping KaillerUser for %d", id)
      if (!threadIsActive) {
        logger.atFine().log("%s thread stop request ignored: not running!", this)
        return
      }
      if (stopFlag) {
        logger.atFine().log("%s thread stop request ignored: already stopping!", this)
        return
      }
      stopFlag = true
      delay(500.milliseconds)
      addEvent(StopFlagEvent())
    }
    listener.stop()
    eventChannel.close()
  }

  @Synchronized
  override fun droppedPacket() {
    game?.droppedPacket(this)
  }

  // server actions
  @Synchronized
  @Throws(
      PingTimeException::class,
      ClientAddressException::class,
      ConnectionTypeException::class,
      UserNameException::class,
      LoginException::class)
  override suspend fun login() {
    updateLastActivity()
    server.login(this)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  override fun chat(message: String) {
    updateLastActivity()
    server.chat(this, message)
    lastChatTime = System.currentTimeMillis()
  }

  @Synchronized
  @Throws(GameKickException::class)
  override fun gameKick(userID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s kick User $userID failed: Not in a game", this)
      throw GameKickException(EmuLang.getString("KailleraUserImpl.KickErrorNotInGame"))
    }
    game?.kick(this, userID)
  }

  @Synchronized
  @Throws(CreateGameException::class, FloodException::class)
  override suspend fun createGame(romName: String): KailleraGame {
    updateLastActivity()
    requireNotNull(server.getUser(id)) { "$this create game failed: User don't exist!" }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("%s create game failed: User status is Playing!", this)
      throw CreateGameException(EmuLang.getString("KailleraUserImpl.CreateGameErrorAlreadyInGame"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("%s create game failed: User status is Connecting!", this)
      throw CreateGameException(
          EmuLang.getString("KailleraUserImpl.CreateGameErrorNotFullyConnected"))
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
      CloseGameException::class)
  override fun quit(message: String?) {
    updateLastActivity()
    server.quit(this, message)
    loggedIn = false
  }

  @Synchronized
  @Throws(JoinGameException::class)
  override suspend fun joinGame(gameID: Int): KailleraGame {
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
      logger.atWarning().log("%s join game failed: Game $gameID does not exist!", this)
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
    this.game = game as KailleraGameImpl?
    gameDataErrorTime = -1
    return game
  }

  // game actions
  @Synchronized
  @Throws(GameChatException::class)
  override fun gameChat(message: String, messageID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s game chat failed: Not in a game", this)
      throw GameChatException(EmuLang.getString("KailleraUserImpl.GameChatErrorNotInGame"))
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

    /*if(this == null){
    	throw new GameChatException("You don't exist!");
    }*/ game!!.chat(this, message)
  }

  @Synchronized
  @Throws(DropGameException::class)
  override fun dropGame() {
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
  override fun quitGame() {
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
  override fun startGame() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s start game failed: Not in a game", this)
      throw StartGameException(EmuLang.getString("KailleraUserImpl.StartGameErrorNotInGame"))
    }
    game!!.start(this)
  }

  @Synchronized
  @Throws(UserReadyException::class)
  override fun playerReady() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("%s player ready failed: Not in a game", this)
      throw UserReadyException(EmuLang.getString("KailleraUserImpl.PlayerReadyErrorNotInGame"))
    }
    if (playerNumber > game!!.playerActionQueue!!.size ||
        game!!.playerActionQueue!![playerNumber - 1].synched) {
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
            // Effectively this is the delay that is allowed before calling it a lag spike.
            .plusMillis(70)
    game!!.ready(this, playerNumber)
  }

  @Throws(GameDataException::class)
  override fun addGameData(data: ByteArray) {
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
            EmuLang.getString("KailleraUserImpl.GameDataErrorNotInGame"),
            data,
            connectionType.byteValue.toInt(),
            playerNumber = 1,
            numPlayers = 1)
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
        addEvent(GameDataEvent(game as KailleraGameImpl, response))
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
    val trySend = eventChannel.trySend(event)
    if (!trySend.isSuccess) {
      logger
          .atSevere()
          .withStackTrace(StackSize.FULL)
          .log("Failed to add event to queue: %s", trySend)
    }
  }

  // TODO(nue): Get rid of this for loop. We should be able to trigger event listeners as soon as
  // the new data is added.
  override suspend fun run(globalContext: CoroutineContext) {
    threadIsActive = true
    logger.atFine().log("%s thread running...", this)
    try {
      while (!stopFlag) {
        val event = eventChannel.receive()
        if (event is StopFlagEvent) {
          break
        }
        listener.actionPerformed(event)
        if (event is GameStartedEvent) {
          status = UserStatus.PLAYING
          if (improvedLagstat) {
            lastUpdate = Instant.now()
          }
        } else if (event is UserQuitEvent && event.user == this) {
          stop()
        }
      }
    } catch (e: InterruptedException) {
      logger.atSevere().withCause(e).log("%s thread interrupted!", this)
    } catch (e: Throwable) {
      logger.atSevere().withCause(e).log("%s thread caught unexpected exception!", this)
    } finally {
      threadIsActive = false
      logger.atFine().log("%s thread exiting...", this)
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
