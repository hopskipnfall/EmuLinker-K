package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.Throws
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.KailleraEvent
import org.emulinker.kaillera.model.event.KailleraEventListener
import org.emulinker.kaillera.model.event.StopFlagEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.*
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.util.EmuLang
import org.emulinker.util.EmuUtil
import org.emulinker.util.Executable

class KailleraUser(
  val id: Int,
  val protocol: String,
  val connectSocketAddress: InetSocketAddress,
  val listener: KailleraEventListener,
  val server: KailleraServer,
  flags: RuntimeFlags
) : Executable {
  var inStealthMode = false

  /** Example: "Project 64k 0.13 (01 Aug 2003)" */
  var clientType: String? = null
    set(clientType) {
      field = clientType
      if (clientType != null && clientType.startsWith(EMULINKER_CLIENT_NAME))
        isEmuLinkerClient = true
    }

  private val initTime = System.currentTimeMillis()

  var connectionType: ConnectionType =
    ConnectionType.DISABLED // TODO(nue): This probably shouldn't have a default.
  var ping = 0
  var socketAddress: InetSocketAddress? = null
  var status = UserStatus.PLAYING // TODO(nue): This probably shouldn't have a default value..
  /**
   * Level of access that the user has.
   *
   * See AdminCommandAction for available values. This should be turned into an enum.
   */
  var accessLevel = 0
  var isEmuLinkerClient = false
    private set
  val connectTime: Long = initTime
  var timeouts = 0
  var lastActivity: Long = initTime
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
    lastKeepAlive = System.currentTimeMillis()
    lastActivity = lastKeepAlive
  }

  var lastKeepAlive: Long = initTime
    private set
  var lastChatTime: Long = initTime
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

  var playerNumber = -1
  var ignoreAll = false
  var isAcceptingDirectMessages = true
  var lastMsgID = -1
  var isMuted = false

  private val lostInput: MutableList<ByteArray> = ArrayList()
  /** Note that this is a different type from lostInput. */
  fun getLostInput(): ByteArray {
    return lostInput[0]
  }

  private val ignoredUsers: MutableList<String> = ArrayList()
  private var gameDataErrorTime: Long = -1

  override var threadIsActive = false
    private set

  private var stopFlag = false
  private val eventQueue: BlockingQueue<KailleraEvent> = LinkedBlockingQueue()

  var tempDelay = 0

  val users: Collection<KailleraUser>
    get() = server.users

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

  override fun toString(): String {
    val n = name
    return if (n == null) {
      "User$id(${connectSocketAddress.address.hostAddress})"
    } else {
      "User$id(${if (n.length > 15) n.take(15) + "..." else n}/${connectSocketAddress.address.hostAddress})"
    }
  }

  var name: String? = null

  fun updateLastKeepAlive() {
    lastKeepAlive = System.currentTimeMillis()
  }

  var game: KailleraGameImpl? = null
    set(value) {
      if (value == null) {
        playerNumber = -1
      }
      field = value
    }

  val accessStr: String
    get() = AccessManager.ACCESS_NAMES[accessLevel]

  override fun equals(other: Any?): Boolean {
    return other is KailleraUser && other.id == id
  }

  fun toDetailedString(): String {
    return ("KailleraUser[id=$id protocol=$protocol status=$status name=$name clientType=$clientType ping=$ping connectionType=$connectionType remoteAddress=" +
      (if (socketAddress == null) EmuUtil.formatSocketAddress(connectSocketAddress)
      else EmuUtil.formatSocketAddress(socketAddress!!)) +
      "]")
  }

  override fun stop() {
    synchronized(this) {
      if (!threadIsActive) {
        logger.atFine().log("$this  thread stop request ignored: not running!")
        return
      }
      if (stopFlag) {
        logger.atFine().log("$this  thread stop request ignored: already stopping!")
        return
      }
      stopFlag = true
      try {
        Thread.sleep(500)
      } catch (e: Exception) {}
      addEvent(StopFlagEvent())
    }
    listener.stop()
  }

  @Synchronized
  fun droppedPacket() {
    if (game != null) {
      // if(game.getStatus() == KailleraGame.STATUS_PLAYING){
      game!!.droppedPacket(this)
      // }
    }
  }

  // server actions
  @Synchronized
  @Throws(
    PingTimeException::class,
    ClientAddressException::class,
    ConnectionTypeException::class,
    UserNameException::class,
    LoginException::class
  )
  fun login() {
    updateLastActivity()
    server.login(this)
  }

  @Synchronized
  @Throws(ChatException::class, FloodException::class)
  fun chat(message: String?) {
    updateLastActivity()
    server.chat(this, message)
    lastChatTime = System.currentTimeMillis()
  }

  @Synchronized
  @Throws(GameKickException::class)
  fun gameKick(userID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("$this kick User $userID failed: Not in a game")
      throw GameKickException(EmuLang.getString("KailleraUser.KickErrorNotInGame"))
    }
    game!!.kick(this, userID)
  }

  @Synchronized
  @Throws(CreateGameException::class, FloodException::class)
  fun createGame(romName: String?): KailleraGame? {
    updateLastActivity()
    if (server.getUser(id) == null) {
      logger.atSevere().log("$this create game failed: User don't exist!")
      return null
    }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("$this create game failed: User status is Playing!")
      throw CreateGameException(EmuLang.getString("KailleraUser.CreateGameErrorAlreadyInGame"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("$this create game failed: User status is Connecting!")
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

  @Synchronized
  @Throws(JoinGameException::class)
  fun joinGame(gameID: Int): KailleraGame {
    updateLastActivity()
    if (game != null) {
      logger.atWarning().log("$this join game failed: Already in: $game")
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorAlreadyInGame"))
    }
    if (status == UserStatus.PLAYING) {
      logger.atWarning().log("$this join game failed: User status is Playing!")
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorAnotherGameRunning"))
    } else if (status == UserStatus.CONNECTING) {
      logger.atWarning().log("$this join game failed: User status is Connecting!")
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorNotFullConnected"))
    }
    val game = server.getGame(gameID)
    if (game == null) {
      logger.atWarning().log("$this join game failed: Game $gameID does not exist!")
      throw JoinGameException(EmuLang.getString("KailleraUser.JoinGameErrorDoesNotExist"))
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
  fun gameChat(message: String, messageID: Int) {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("$this game chat failed: Not in a game")
      throw GameChatException(EmuLang.getString("KailleraUser.GameChatErrorNotInGame"))
    }
    if (isMuted) {
      logger.atWarning().log("$this gamechat denied: Muted: $message")
      game!!.announce("You are currently muted!", this)
      return
    }
    if (server.accessManager.isSilenced(socketAddress!!.address)) {
      logger.atWarning().log("$this gamechat denied: Silenced: $message")
      game!!.announce("You are currently silenced!", this)
      return
    }

    /*if(this == null){
    	throw new GameChatException("You don't exist!");
    }*/ game!!.chat(this, message)
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
    } else logger.atFine().log("$this drop game failed: Not in a game")
  }

  @Synchronized
  @Throws(DropGameException::class, QuitGameException::class, CloseGameException::class)
  fun quitGame() {
    updateLastActivity()
    if (game == null) {
      logger.atFine().log("$this quit game failed: Not in a game")
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
    addEvent(UserQuitGameEvent(game, this))
  }

  @Synchronized
  @Throws(StartGameException::class)
  fun startGame() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("$this start game failed: Not in a game")
      throw StartGameException(EmuLang.getString("KailleraUser.StartGameErrorNotInGame"))
    }
    game!!.start(this)
  }

  @Synchronized
  @Throws(UserReadyException::class)
  fun playerReady() {
    updateLastActivity()
    if (game == null) {
      logger.atWarning().log("$this player ready failed: Not in a game")
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
        // Effectively this is the delay that is allowed before calling it a lag spike.
        .plusMillis(70)
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
      }
    }

    updateLastActivity()
    try {
      if (game == null)
        throw GameDataException(
          EmuLang.getString("KailleraUser.GameDataErrorNotInGame"),
          data,
          connectionType.byteValue.toInt(),
          1,
          1
        )

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
      logger.atFine().withCause(e).log("$this add game data failed")

      // i'm going to reflect the game data packet back at the user to prevent game lockups,
      // but this uses extra bandwidth, so we'll set a counter to prevent people from leaving
      // games running for a long time in this state
      if (gameDataErrorTime > 0) {
        if (
          System.currentTimeMillis() - gameDataErrorTime > 30000
        ) // give the user time to close the game
        {
          // this should be warn level, but it creates tons of lines in the log
          logger.atFine().log("$this: error game data exceeds drop timeout!")
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

  fun addEvent(event: KailleraEvent?) {
    if (event == null) {
      logger.atSevere().log("$this: ignoring null event!")
      return
    }
    if (status != UserStatus.IDLE) {
      if (ignoringUnnecessaryServerActivity) {
        if (event.toString() == "InfoMessageEvent") return
      }
    }
    eventQueue.offer(event)
  }

  override fun run() {
    threadIsActive = true
    logger.atFine().log("$this thread running...")
    try {
      while (!stopFlag) {
        val event = eventQueue.poll(200, TimeUnit.SECONDS)
        if (event == null) continue else if (event is StopFlagEvent) break
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
      logger.atSevere().withCause(e).log("$this thread interrupted!")
    } catch (e: Throwable) {
      logger.atSevere().withCause(e).log("$this thread caught unexpected exception!")
    } finally {
      threadIsActive = false
      logger.atFine().log("$this thread exiting...")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val EMULINKER_CLIENT_NAME = "EmulinkerSF Admin Client"
  }
}
