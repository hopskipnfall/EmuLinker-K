package org.emulinker.kaillera.controller.v086

import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.plus
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.KailleraServerController
import org.emulinker.kaillera.controller.v086.action.ACKAction
import org.emulinker.kaillera.controller.v086.action.CachedGameDataAction
import org.emulinker.kaillera.controller.v086.action.ChatAction
import org.emulinker.kaillera.controller.v086.action.CloseGameAction
import org.emulinker.kaillera.controller.v086.action.CreateGameAction
import org.emulinker.kaillera.controller.v086.action.DropGameAction
import org.emulinker.kaillera.controller.v086.action.GameChatAction
import org.emulinker.kaillera.controller.v086.action.GameDataAction
import org.emulinker.kaillera.controller.v086.action.GameDesynchAction
import org.emulinker.kaillera.controller.v086.action.GameInfoAction
import org.emulinker.kaillera.controller.v086.action.GameKickAction
import org.emulinker.kaillera.controller.v086.action.GameStatusAction
import org.emulinker.kaillera.controller.v086.action.GameTimeoutAction
import org.emulinker.kaillera.controller.v086.action.InfoMessageAction
import org.emulinker.kaillera.controller.v086.action.JoinGameAction
import org.emulinker.kaillera.controller.v086.action.KeepAliveAction
import org.emulinker.kaillera.controller.v086.action.LoginAction
import org.emulinker.kaillera.controller.v086.action.PlayerDesynchAction
import org.emulinker.kaillera.controller.v086.action.QuitAction
import org.emulinker.kaillera.controller.v086.action.QuitGameAction
import org.emulinker.kaillera.controller.v086.action.StartGameAction
import org.emulinker.kaillera.controller.v086.action.UserReadyAction
import org.emulinker.kaillera.controller.v086.action.V086Action
import org.emulinker.kaillera.controller.v086.action.V086GameEventHandler
import org.emulinker.kaillera.controller.v086.action.V086ServerEventHandler
import org.emulinker.kaillera.controller.v086.action.V086UserEventHandler
import org.emulinker.kaillera.controller.v086.protocol.AllReady
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.Chat
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.CreateGame
import org.emulinker.kaillera.controller.v086.protocol.GameChat
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.controller.v086.protocol.GameKick
import org.emulinker.kaillera.controller.v086.protocol.JoinGame
import org.emulinker.kaillera.controller.v086.protocol.KeepAlive
import org.emulinker.kaillera.controller.v086.protocol.PlayerDrop
import org.emulinker.kaillera.controller.v086.protocol.Quit
import org.emulinker.kaillera.controller.v086.protocol.QuitGame
import org.emulinker.kaillera.controller.v086.protocol.StartGame
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.event.AllReadyEvent
import org.emulinker.kaillera.model.event.ChatEvent
import org.emulinker.kaillera.model.event.ConnectedEvent
import org.emulinker.kaillera.model.event.GameChatEvent
import org.emulinker.kaillera.model.event.GameClosedEvent
import org.emulinker.kaillera.model.event.GameCreatedEvent
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.event.GameDesynchEvent
import org.emulinker.kaillera.model.event.GameEvent
import org.emulinker.kaillera.model.event.GameInfoEvent
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.GameStatusChangedEvent
import org.emulinker.kaillera.model.event.GameTimeoutEvent
import org.emulinker.kaillera.model.event.InfoMessageEvent
import org.emulinker.kaillera.model.event.PlayerDesynchEvent
import org.emulinker.kaillera.model.event.ServerEvent
import org.emulinker.kaillera.model.event.UserDroppedGameEvent
import org.emulinker.kaillera.model.event.UserEvent
import org.emulinker.kaillera.model.event.UserJoinedEvent
import org.emulinker.kaillera.model.event.UserJoinedGameEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException
import org.emulinker.net.BindException
import org.emulinker.util.EmuUtil.threadSleep

/** High level logic for handling messages on a port. Not tied to an individual user. */
@Singleton
class V086Controller
@Inject
internal constructor(
  override var server: KailleraServer,
  var threadPool: ThreadPoolExecutor,
  config: Configuration,
  loginAction: LoginAction,
  ackAction: ACKAction,
  chatAction: ChatAction,
  createGameAction: CreateGameAction,
  joinGameAction: JoinGameAction,
  keepAliveAction: KeepAliveAction,
  quitGameAction: QuitGameAction,
  quitAction: QuitAction,
  startGameAction: StartGameAction,
  gameChatAction: GameChatAction,
  gameKickAction: GameKickAction,
  userReadyAction: UserReadyAction,
  cachedGameDataAction: CachedGameDataAction,
  gameDataAction: GameDataAction,
  dropGameAction: DropGameAction,
  closeGameAction: CloseGameAction,
  gameStatusAction: GameStatusAction,
  gameDesynchAction: GameDesynchAction,
  playerDesynchAction: PlayerDesynchAction,
  gameInfoAction: GameInfoAction,
  gameTimeoutAction: GameTimeoutAction,
  infoMessageAction: InfoMessageAction,
  private val v086ClientHandlerFactory: V086ClientHandler.Factory,
  flags: RuntimeFlags,
) : KailleraServerController {
  var isRunning = false
    private set

  override val clientTypes: Array<String> =
    config.getStringArray("controllers.v086.clientTypes.clientType")

  var clientHandlers: MutableMap<Int, V086ClientHandler> = ConcurrentHashMap()

  private val portRangeStart: Int = config.getInt("controllers.v086.portRangeStart")
  private val extraPorts: Int = config.getInt("controllers.v086.extraPorts", 0)

  var portRangeQueue: Queue<Int> = ConcurrentLinkedQueue()

  val serverEventHandlers: Map<KClass<out ServerEvent>, V086ServerEventHandler<Nothing>> =
    mapOf(
      ChatEvent::class to chatAction,
      GameCreatedEvent::class to createGameAction,
      UserJoinedEvent::class to loginAction,
      GameClosedEvent::class to closeGameAction,
      UserQuitEvent::class to quitAction,
      GameStatusChangedEvent::class to gameStatusAction,
    )
  val gameEventHandlers: Map<KClass<out GameEvent>, V086GameEventHandler<Nothing>> =
    mapOf(
      UserJoinedGameEvent::class to joinGameAction,
      UserQuitGameEvent::class to quitGameAction,
      GameStartedEvent::class to startGameAction,
      GameChatEvent::class to gameChatAction,
      AllReadyEvent::class to userReadyAction,
      GameDataEvent::class to gameDataAction,
      UserDroppedGameEvent::class to dropGameAction,
      GameDesynchEvent::class to gameDesynchAction,
      PlayerDesynchEvent::class to playerDesynchAction,
      GameInfoEvent::class to gameInfoAction,
      GameTimeoutEvent::class to gameTimeoutAction,
    )
  val userEventHandlers: Map<KClass<out UserEvent>, V086UserEventHandler<Nothing>> =
    mapOf(
      ConnectedEvent::class to ackAction,
      InfoMessageEvent::class to infoMessageAction,
    )

  var actions: Array<V086Action<*>?> = arrayOfNulls(25)

  override val version = "v086"

  override val numClients = clientHandlers.size

  override val bufferSize = flags.v086BufferSize

  override fun toString(): String {
    return "V086Controller[clients=${clientHandlers.size} isRunning=$isRunning]"
  }

  /**
   * Receives new connections and delegates to a new V086ClientHandler instance for communication
   * over a separate port.
   */
  @Throws(ServerFullException::class, NewConnectionException::class)
  override fun newConnection(clientSocketAddress: InetSocketAddress, protocol: String): Int {
    if (!isRunning) throw NewConnectionException("Controller is not running")
    val clientHandler = v086ClientHandlerFactory.create(clientSocketAddress, this)
    val user = server.newConnection(clientSocketAddress, protocol, clientHandler)
    var boundPort = -1
    var bindAttempts = 0
    while (bindAttempts++ < 5) {
      val portInteger = portRangeQueue.poll()
      if (portInteger == null) {
        throw NewConnectionException("No ports are available to bind for $user")
      } else {
        val port = portInteger.toInt()
        logger.atInfo().log("Allocating private port %d for: %s", port, user)
        try {
          clientHandler.bind(port)
          boundPort = port
          break
        } catch (e: BindException) {
          logger.atSevere().withCause(e).log("Failed to bind to port %d for: %s", port, user)
          logger
            .atFine()
            .log(
              "%s returning port %d to available port queue: %d available",
              this,
              port,
              portRangeQueue.size + 1
            )
          portRangeQueue.add(port)
        }
      }
      // pause very briefly to give the OS a chance to free a port
      threadSleep(5.milliseconds)
    }
    if (boundPort < 0) {
      clientHandler.stop()
      throw NewConnectionException("Failed to bind!")
    }
    clientHandler.start(user!!)
    return boundPort
  }

  @Synchronized
  override fun start() {
    isRunning = true
  }

  @Synchronized
  override fun stop() {
    isRunning = false
    clientHandlers.values.forEach { it.stop() }
    clientHandlers.clear()
  }

  init {
    var maxPort = 0
    for (i in portRangeStart..portRangeStart + server.maxUsers + extraPorts) {
      portRangeQueue.add(i)
      maxPort = i
    }
    logger
      .atWarning()
      .log(
        "Listening on UDP ports: %d to %d.  Make sure these ports are open in your firewall!",
        portRangeStart,
        maxPort
      )

    // array access should be faster than a hash and we won't have to create
    // a new Integer each time
    actions[UserInformation.ID.toInt()] = loginAction
    actions[ClientAck.ID.toInt()] = ackAction
    actions[Chat.ID.toInt()] = chatAction
    actions[CreateGame.ID.toInt()] = createGameAction
    actions[JoinGame.ID.toInt()] = joinGameAction
    actions[KeepAlive.ID.toInt()] = keepAliveAction
    actions[QuitGame.ID.toInt()] = quitGameAction
    actions[Quit.ID.toInt()] = quitAction
    actions[StartGame.ID.toInt()] = startGameAction
    actions[GameChat.ID.toInt()] = gameChatAction
    actions[GameKick.ID.toInt()] = gameKickAction
    actions[AllReady.ID.toInt()] = userReadyAction
    actions[CachedGameData.ID.toInt()] = cachedGameDataAction
    actions[GameData.ID.toInt()] = gameDataAction
    actions[PlayerDrop.ID.toInt()] = dropGameAction
  }

  companion object {
    const val MAX_BUNDLE_SIZE = 9

    private val logger = FluentLogger.forEnclosingClass()
  }
}
