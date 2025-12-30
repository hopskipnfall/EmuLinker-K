package org.emulinker.kaillera.model.exception

import com.google.common.flogger.FluentLogger
import io.netty.buffer.ByteBuf

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
    val r = io.netty.buffer.Unpooled.buffer(arraySize)
    // Initialize with zeros or whatever usage pattern implies (it implies we construct a response)
    r.writeZero(arraySize)

    // We do need to copy data from input 'data' to 'r' at specific offsets.
    // Logic:
    // for (actionCounter in 0 until actionsPerMessage) {
    //   System.arraycopy(data, 0, r, offset, bytesPerAction)
    // }
    // Wait, original logic:
    // System.arraycopy(data.toByteArray(), 0, r.bytes, offset, bytesPerAction)
    // It copied from specific source always 0? 
    // Yes: "data.toByteArray(), 0"
    // So it copies the SAME action (or whatever data is) multiple times?
    // "data" is probably "input data" for this frame?
    // Wait, "data.toByteArray()" returns the array backing the VariableSizeByteArray.
    // If VariableSizeByteArray just holds bytes, then "data.toByteArray()" is the content.
    // It seems it copies the *entire* incoming data chunk (assumed to be 1 action?) into multiple slots?
    // Input `data` represents ONE action or something?
    // But `val bytesPerAction = data.size / actionsPerMessage`.
    // So `data` contains `actionsPerMessage` actions?
    // But `System.arraycopy(..., 0, ..., ..., bytesPerAction)`.
    // It takes from index 0 of `data`.
    // So if `data` has multiple actions, it ALWAYS takes the FIRST one (index 0 to bytesPerAction).
    // This looks like logic to replicate the first action into the response for specific player?
    // Or maybe it's "GameData error" -> "Construct a response that fills missing info"?
    // I will preserve the logic: Copy `bytesPerAction` from `data` at index 0.

    val dataArr = ByteArray(bytesPerAction)
    data.getBytes(data.readerIndex(), dataArr) // Read first bytesPerAction bytes

    for (actionCounter in 0 until actionsPerMessage) {
      val destIndex = actionCounter * (numPlayers * bytesPerAction) + (playerNumber - 1) * bytesPerAction
      r.setBytes(destIndex, dataArr)
    }
    r.writerIndex(arraySize)

    response = r
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
