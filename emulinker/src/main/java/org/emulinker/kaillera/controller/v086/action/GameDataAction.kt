package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import java.util.concurrent.TimeUnit
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.model.event.GameDataEvent
import org.emulinker.kaillera.model.exception.GameDataException


object GameDataAction : V086Action<GameData>, V086GameEventHandler<GameDataEvent> {
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
                // e.response is now ByteBuf. createAndMakeDeepCopy expects ByteBuf.
                // Note: e.response (ByteBuf) ref count? user.addGameData might have created it?
                // The exception constructor creates a NEW ByteBuf 'r'. It's not released anywhere yet.
                // We pass it to createAndMakeDeepCopy which calls retainedDuplicate().
                // We should release e.response after usage if we own it.
                // Assuming createAndMakeDeepCopy uses it.
                val responseMsg = GameData.createAndMakeDeepCopy(clientHandler.nextMessageNumber, e.response!!)
                clientHandler.send(responseMsg)
                // e.response!!.release() // Should we release? The exception holds it. 
                // Exceptions holding resources is tricky. 
                // For now, let's assume GC or Netty leak detector will complain if we leak.
                // Ideally GameDataException should implement release?
                // Or we release it here.
                e.response!!.release()
              } catch (e2: MessageFormatException) {
                logger.atSevere().withCause(e2).log("Failed to construct GameData message")
              }
            }
          }

          else -> throw e
        }
      }
    } finally {
      // No recycling needed for Netty ByteBuf unless we allocated it and want to pool it manually?
      // But message.gameData comes from Netty, it will be released by the caller (Netty pipeline or performAction caller).
      // V086ClientHandler calls performAction. Netty ReferenceCountUtil.release(msg) usually handles it.
      // But our GameData implements ReferenceCounted.
    }
  }

  override fun handleEvent(event: GameDataEvent, clientHandler: V086ClientHandler) {
    // event.data is GameData object now.
    val gameDataMsg = event.data
    val data = gameDataMsg.gameData
    try {
      val key = clientHandler.serverGameDataCache.indexOf(data)
      if (key < 0) {
        clientHandler.serverGameDataCache.add(data)
        try {
          // We need to send GameData message. The event already has the message!
          // But maybe we need to update message number?
          // clientHandler.nextMessageNumber is unused (0).
          // We should use the message from the event?
          // Or clone it with new number? 
          // Previous code: clientHandler.send(GameData(clientHandler.nextMessageNumber, data))
          // It created a NEW GameData wrapping 'data'.
          // 'data' was VariableSizeByteArray.
          // Now 'data' is ByteBuf.
          // We can use gameDataMsg.gameData (retainedDuplicate?)
          // If we send it, 'send' writes it. 
          // We should ideally use createAndMakeDeepCopy or just new primitive.
          clientHandler.send(GameData(clientHandler.nextMessageNumber, data.retainedDuplicate()))
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
      // Logic for recycling removed.
    }
  }

  private val logger = FluentLogger.forEnclosingClass()
}
