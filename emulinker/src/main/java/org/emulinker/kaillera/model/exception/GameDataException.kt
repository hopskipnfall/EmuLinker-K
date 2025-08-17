package org.emulinker.kaillera.model.exception

import com.google.common.flogger.FluentLogger
import org.emulinker.util.VariableSizeByteArray

class GameDataException : ActionException {
  var response: VariableSizeByteArray? = null
    private set

  constructor(message: String?) : super(message)

  constructor(
    message: String?,
    data: VariableSizeByteArray,
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
    val r = VariableSizeByteArray(ByteArray(arraySize))

    for (actionCounter in 0 until actionsPerMessage) {
      System.arraycopy(
        data.toByteArray(),
        0,
        r.bytes,
        actionCounter * (numPlayers * bytesPerAction) + (playerNumber - 1) * bytesPerAction,
        bytesPerAction,
      )
    }
    response = r
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
