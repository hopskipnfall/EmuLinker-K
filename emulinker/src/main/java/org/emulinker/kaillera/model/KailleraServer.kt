package org.emulinker.kaillera.model

import java.net.InetSocketAddress
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.exception.ChatException
import org.emulinker.kaillera.model.exception.ClientAddressException
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.ConnectionTypeException
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
import org.emulinker.kaillera.model.impl.KailleraGameImpl
import org.emulinker.kaillera.model.impl.KailleraUserImpl
import org.emulinker.kaillera.model.impl.Trivia
import org.emulinker.kaillera.release.ReleaseInfo

interface KailleraServer {
  val maxUsers: Int
  val maxGames: Int
  val maxPing: Int

  val releaseInfo: ReleaseInfo
  val accessManager: AccessManager
  val users: Collection<KailleraUserImpl>
  val games: Collection<KailleraGameImpl>
  var trivia: Trivia?
  var switchTrivia: Boolean

  fun announce(message: String, gamesAlso: Boolean)
  fun announce(message: String, gamesAlso: Boolean, targetUser: KailleraUserImpl?)
  fun getUser(userID: Int): KailleraUser?
  fun getGame(gameID: Int): KailleraGame?
  fun checkMe(user: KailleraUser, message: String): Boolean

  @Throws(ServerFullException::class, NewConnectionException::class)
  fun newConnection(
    clientSocketAddress: InetSocketAddress,
    protocol: String,
    // For acting on KailleraEvents.
    v086ClientHandler: V086ClientHandler
  ): KailleraUser

  @Throws(
    PingTimeException::class,
    ClientAddressException::class,
    ConnectionTypeException::class,
    UserNameException::class,
    LoginException::class
  )
  suspend fun login(user: KailleraUser)

  @Throws(ChatException::class, FloodException::class) fun chat(user: KailleraUser, message: String)

  @Throws(CreateGameException::class, FloodException::class)
  suspend fun createGame(user: KailleraUser, romName: String): KailleraGame

  @Throws(
    QuitException::class,
    DropGameException::class,
    QuitGameException::class,
    CloseGameException::class
  )
  fun quit(user: KailleraUser, message: String?)
  suspend fun stop()
}
