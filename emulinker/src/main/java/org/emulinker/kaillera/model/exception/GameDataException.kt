package org.emulinker.kaillera.model.exception

import com.google.common.flogger.FluentLogger
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator

class GameDataException : ActionException {
  var response: ByteBuf? = null
    private set

  constructor(message: String?) : super(message)

  constructor(
    message: String?,
    data: ByteBuf,
    actionsPerMessage: Int,
    playerNumber: Int,
    numPlayers: Int,
  ) : super(message) {
    if (actionsPerMessage == 0) {
      logger.atWarning().log("Avoided divide by zero error..")
      return
    }
    val bytesPerAction = data.readableBytes() / actionsPerMessage
    val arraySize = numPlayers * actionsPerMessage * bytesPerAction
    val r = PooledByteBufAllocator.DEFAULT.buffer(arraySize).writeZero(arraySize)

    for (actionCounter in 0 until actionsPerMessage) {
      val srcIndex = actionCounter * bytesPerAction
      val dstIndex =
        actionCounter * (numPlayers * bytesPerAction) + (playerNumber - 1) * bytesPerAction
      data.getBytes(data.readerIndex() + srcIndex, r, dstIndex, bytesPerAction)
    }
    response = r
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
