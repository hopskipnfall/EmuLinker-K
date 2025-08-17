package org.emulinker.kaillera.model.exception

import com.google.common.flogger.FluentLogger

class GameDataException : ActionException {
  var response: ByteArray? = null
    private set

  constructor(message: String?) : super(message)

  constructor(
    message: String?,
    data: ByteArray,
    actionsPerMessage: Int,
    playerNumber: Int,
    numPlayers: Int,
  ) : super(message) {
    if (actionsPerMessage == 0) {
      logger.atWarning().log("Avoided divide by zero error..")
      return
    }
    val bytesPerAction = data.size / actionsPerMessage
    val arraySize = numPlayers * actionsPerMessage * bytesPerAction
    response = ByteArray(arraySize)
    for (actionCounter in 0 until actionsPerMessage) {
      System.arraycopy(
        data,
        0,
        response,
        actionCounter * (numPlayers * bytesPerAction) + (playerNumber - 1) * bytesPerAction,
        bytesPerAction,
      )
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
