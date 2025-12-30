package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import com.google.common.flogger.LazyArg
import io.github.hopskipnfall.kaillera.protocol.model.UserStatus
import io.github.hopskipnfall.kaillera.protocol.v086.ClientAck
import io.github.hopskipnfall.kaillera.protocol.v086.ConnectionRejected
import io.github.hopskipnfall.kaillera.protocol.v086.ServerAck
import io.github.hopskipnfall.kaillera.protocol.v086.ServerStatus
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.CLIENT_WITH_BYTE_ID_BUG
import org.emulinker.kaillera.model.event.ConnectedEvent
import org.emulinker.kaillera.model.event.UserEvent
import org.emulinker.kaillera.model.exception.LoginException
import org.emulinker.kaillera.model.toValueForBrokenClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ACKAction : V086Action<ClientAck>, V086UserEventHandler<UserEvent>, KoinComponent {
  override fun toString() = "ACKAction"

  private val flags: RuntimeFlags by inject()

  @Throws(FatalActionException::class)
  override fun performAction(message: ClientAck, clientHandler: V086ClientHandler) {
    val user = clientHandler.user
    if (user.loggedIn) {
      return
    }
    clientHandler.addSpeedMeasurement()
    if (clientHandler.speedMeasurementCount > clientHandler.numAcksForSpeedTest) {
      user.ping = clientHandler.averageNetworkSpeed
      logger
        .atFine()
        .log(
          "Calculated %s ping time: average=%s, best=%d",
          user,
          clientHandler.averageNetworkSpeed,
          clientHandler.bestNetworkSpeed,
        )
      user.login().onFailure { e ->
        when (e) {
          is LoginException -> {
            try {
              clientHandler.send(
                ConnectionRejected(
                  clientHandler.nextMessageNumber,
                  // TODO(nue): Localize this?
                  username = "server",
                  user.id,
                  e.message ?: "",
                )
              )
            } catch (e2: MessageFormatException) {
              logger.atSevere().withCause(e2).log("Failed to construct new ConnectionRejected")
            }
            throw FatalActionException("Login failed: " + e.message)
          }
          else -> throw e
        }
      }
    } else {
      try {
        clientHandler.send(ServerAck(clientHandler.nextMessageNumber))
      } catch (e: MessageFormatException) {
        logger.atSevere().withCause(e).log("Failed to construct new ACK.ServerACK")
        return
      }
    }
  }

  override fun handleEvent(event: UserEvent, clientHandler: V086ClientHandler) {
    val connectedEvent = event as ConnectedEvent
    val server = connectedEvent.server
    val thisUser = connectedEvent.user
    val users = mutableListOf<ServerStatus.User>()
    val games = mutableListOf<ServerStatus.Game>()
    val switchStatuses: Boolean =
      flags.switchStatusBytesForBuggyClient &&
        clientHandler.user.clientType == CLIENT_WITH_BYTE_ID_BUG

    for (user in server.usersMap.values) {
      if (user.status != UserStatus.CONNECTING && user != thisUser)
        users.add(
          ServerStatus.User(
            user.name!!,
            user.ping,
            if (switchStatuses) user.status.toValueForBrokenClient() else user.status,
            user.id,
            user.connectionType,
          )
        )
    }
    for (game in server.gamesMap.values) {
      val num = game.players.count { !it.inStealthMode }
      games.add(
        ServerStatus.Game(
          game.romName,
          game.id,
          game.clientType!!,
          game.owner.name!!,
          "$num/${game.maxUsers}",
          if (switchStatuses) game.status.toValueForBrokenClient() else game.status,
        )
      )
    }

    // Here I am attempting to fix the inherent Kaillera protocol bug that occurs when there are a
    // large number of users
    // and/or games on the server.  The size of the ServerStatus packet can be very large, and
    // depending on the router
    // settings or os config, the packet size exceeds a UDP/IP limit and gets dropped.  This results
    // in the user getting
    // half logged-in, in a weird state.

    // I am attempting to fix this by breaking the ServerStatus message up into multiple packets.
    // I'm shooting for a max
    // packet size of 1500 bytes, but since kaillera sends 3 messages per packet, the max size for a
    // single message should be 500
    var counter = 0
    var sent = false
    var usersSubList = mutableListOf<ServerStatus.User>()
    var gamesSubList = mutableListOf<ServerStatus.Game>()
    while (users.isNotEmpty()) {
      val user = users.removeFirst()
      if (counter + user.numBytes >= 300) {
        sendServerStatus(clientHandler, usersSubList, gamesSubList, counter)
        usersSubList = mutableListOf()
        gamesSubList = mutableListOf()
        counter = 0
        sent = true
      }
      counter += user.numBytes
      usersSubList.add(user)
    }
    while (games.isNotEmpty()) {
      val game = games.removeFirst()
      if (counter + game.numBytes >= 300) {
        sendServerStatus(clientHandler, usersSubList, gamesSubList, counter)
        usersSubList = mutableListOf()
        gamesSubList = mutableListOf()
        counter = 0
        sent = true
      }
      counter += game.numBytes
      gamesSubList.add(game)
    }
    if (usersSubList.isNotEmpty() || gamesSubList.isNotEmpty() || !sent) {
      sendServerStatus(clientHandler, usersSubList, gamesSubList, counter)
    }
  }

  private fun sendServerStatus(
    clientHandler: V086ClientHandler,
    users: List<ServerStatus.User>,
    games: List<ServerStatus.Game>,
    counter: Int,
  ) {
    logger
      .atFine()
      .log(
        "Sending ServerStatus to %s: %d users, %d games in %d bytes, games: %s",
        clientHandler.user,
        users.size,
        games.size,
        counter,
        LazyArg { games.map { it.gameId } },
      )
    try {
      clientHandler.send(ServerStatus(clientHandler.nextMessageNumber, users, games))
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct new ServerStatus for users")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
