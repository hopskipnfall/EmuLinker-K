package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.GameChatNotification
import org.emulinker.kaillera.controller.v086.protocol.GameData.Companion.createAndMakeDeepCopy
import org.emulinker.kaillera.model.exception.GameDataException

object CachedGameDataAction : V086Action<CachedGameData> {
  override fun toString() = "CachedGameDataAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: CachedGameData, clientHandler: V086ClientHandler) {
    val user = clientHandler.user
    val data = clientHandler.clientGameDataCache[message.key]
    try {
      val addGameDataResult = user.addGameData(data)

      addGameDataResult.onFailure { e ->
        when (e) {
          is GameDataException -> {
            logger.atFine().withCause(e).log("Game data error")
            if (e.response != null) {
              try {
                clientHandler.send(
                  createAndMakeDeepCopy(clientHandler.nextMessageNumber, e.response!!)
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
    } finally {
      data.release()
    }
  }

  private val logger = FluentLogger.forEnclosingClass()
}
