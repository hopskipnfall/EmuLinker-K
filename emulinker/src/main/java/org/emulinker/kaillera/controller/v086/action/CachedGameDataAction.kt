package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.concurrent.TimeUnit
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
    val data =
      try {
        clientHandler.clientGameDataCache[message.key]
      } catch (e: IndexOutOfBoundsException) {
        // The key comes from the client, so this is reachable with a buggy or hostile client.
        reportMissingCacheKey(message.key, e, clientHandler)
        return
      }
    try {
      val addGameDataResult = user.addGameData(data)

      addGameDataResult.onFailure { e ->
        when (e) {
          is GameDataException -> {
            logger.atFine().withCause(e).log("Game data error")
            if (e.response != null) {
              try {
                clientHandler.send(createAndMakeDeepCopy(0, e.response!!))
              } catch (e2: MessageFormatException) {
                logger.atSevere().withCause(e2).log("Failed to construct GameData message")
              }
            }
          }

          else -> throw e
        }
      }
    } finally {
      data.release()
    }
  }

  private fun reportMissingCacheKey(
    key: Int,
    e: IndexOutOfBoundsException,
    clientHandler: V086ClientHandler,
  ) {
    logger
      .atWarning()
      .atMostEvery(5, TimeUnit.SECONDS)
      .withCause(e)
      .log("Game data error! The client cached key %s was not found in the cache!", key)

    // This may not always be the best thing to do...
    try {
      clientHandler.send(
        GameChatNotification(0, "Error", "Game Data Error!  Game state will be inconsistent!")
      )
    } catch (e2: MessageFormatException) {
      logger.atSevere().withCause(e2).log("Failed to construct new GameChat.Notification")
    }
  }

  private val logger = FluentLogger.forEnclosingClass()
}
