package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.concurrent.TimeUnit
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.exception.GameDataException
import org.emulinker.util.VariableSizeByteArray

object GameDataAction : V086Action<GameData>, V086GameEventHandler<GameDataEvent> {
  override val actionPerformedCount = 0
  override val handledEventCount = 0

  override fun toString() = "GameDataAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: GameData, clientHandler: V086ClientHandler) {
    val user = clientHandler.user
    val data = message.gameData
    clientHandler.clientGameDataCache.add(data)
    try {
      user.addGameData(data).onFailure { e ->
        when (e) {
          is GameDataException -> {
            logger.atWarning().atMostEvery(5, TimeUnit.SECONDS).withCause(e).log("Game data error")
            if (e.response != null) {
              try {
                clientHandler.send(
                  GameData.createAndMakeDeepCopy(clientHandler.nextMessageNumber, e.response!!)
                )
              } catch (e2: MessageFormatException) {
                logger.atSevere().withCause(e2).log("Failed to construct GameData message")
              }
            }
          }

          else -> throw e
        }
      }
    } finally {
      VariableSizeByteArray.pool.recycle(data)
    }
  }

  override fun handleEvent(event: GameDataEvent, clientHandler: V086ClientHandler) {
    val data = event.data
    try {
      val key = clientHandler.serverGameDataCache.indexOf(data)
      if (key < 0) {
        clientHandler.serverGameDataCache.add(data)
        try {
          clientHandler.send(GameData(clientHandler.nextMessageNumber, data))
        } catch (e: MessageFormatException) {
          logger.atSevere().withCause(e).log("Failed to construct GameData message")
        }
      } else {
        try {
          clientHandler.send(CachedGameData(clientHandler.nextMessageNumber, key))
        } catch (e: MessageFormatException) {
          logger.atSevere().withCause(e).log("Failed to construct CachedGameData message")
        }
      }
    } finally {
      VariableSizeByteArray.pool.recycle(data)
    }
  }

  private val logger = FluentLogger.forEnclosingClass()
}
