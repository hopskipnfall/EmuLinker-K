package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import io.github.hopskipnfall.kaillera.protocol.v086.CachedGameData
import io.github.hopskipnfall.kaillera.protocol.v086.GameChatNotification
import io.github.hopskipnfall.kaillera.protocol.v086.GameData
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.exception.GameDataException

object CachedGameDataAction : V086Action<CachedGameData> {
  override fun toString() = "CachedGameDataAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: CachedGameData, clientHandler: V086ClientHandler) {
    val user = clientHandler.user
    val data = clientHandler.clientGameDataCache[message.key]
    data.inTemporaryUse.set(true)
    val addGameDataResult =
      try {
        user.addGameData(data)
      } finally {
        data.inTemporaryUse.set(false)
      }
    addGameDataResult.onFailure { e ->
      when (e) {
        is GameDataException -> {
          logger.atFine().withCause(e).log("Game data error")
          if (e.response != null) {
            try {
              clientHandler.send(
                GameData(clientHandler.nextMessageNumber, e.response!!.toByteArray().clone())
              )
            } catch (e2: MessageFormatException) {
              logger.atSevere().withCause(e2).log("Failed to construct GameData message")
            }
          }
        }
        is IndexOutOfBoundsException -> {
          logger
            .atSevere()
            .withCause(e)
            .log(
              "Game data error!  The client cached key %s was not found in the cache!",
              message.key,
            )

          // This may not always be the best thing to do...
          try {
            clientHandler.send(
              GameChatNotification(
                clientHandler.nextMessageNumber,
                "Error",
                "Game Data Error!  Game state will be inconsistent!",
              )
            )
          } catch (e2: MessageFormatException) {
            logger.atSevere().withCause(e2).log("Failed to construct new GameChat.Notification")
          }
        }
        else -> throw e
      }
    }
  }

  private val logger = FluentLogger.forEnclosingClass()
}
