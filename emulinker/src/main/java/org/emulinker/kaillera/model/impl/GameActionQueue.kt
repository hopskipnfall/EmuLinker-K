package org.emulinker.kaillera.model.impl

import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled

class GameActionQueue(val numPlayers: Int, private val bufferSize: Int) {
  private val playerBuffers: Array<CompositeByteBuf> =
    Array(numPlayers) { Unpooled.compositeBuffer() }

  private val synced: BooleanArray = BooleanArray(numPlayers) { false }

  fun markSynced(playerNumber: Int) {
    val index = playerNumber - 1
    if (index in 0 until numPlayers) {
      synced[index] = true
      val buf = playerBuffers[index]
      if (buf.numComponents() > 0) {
        buf.removeComponents(0, buf.numComponents())
      }
      buf.clear()
    }
  }

  fun markDesynced(playerNumber: Int) {
    val index = playerNumber - 1
    if (index in 0 until numPlayers) {
      synced[index] = false
      val buf = playerBuffers[index]
      if (buf.refCnt() > 0) {
        // We don't release the wrapper itself, just clear content?
        // PlayerActionQueue logic: if (data.refCnt() > 0) data.release()
        // But data was a val.
        // Here we want to keep the CompositeBuf alive but empty it?
        // actually clearing it is safer.
        if (buf.numComponents() > 0) {
          buf.removeComponents(0, buf.numComponents())
        }
        buf.clear()
      }
    }
  }

  fun desyncAll() {
    for (i in 0 until numPlayers) {
      markDesynced(i + 1)
    }
  }

  fun addActions(playerNumber: Int, actions: ByteBuf) {
    val index = playerNumber - 1
    if (index !in 0 until numPlayers) {
      actions.release()
      return
    }

    if (!synced[index]) {
      actions.release()
      return
    }

    val buf = playerBuffers[index]
    buf.addComponent(true, actions.retain())

    if (buf.readableBytes() > bufferSize) {
      val toDiscard = buf.readableBytes() - bufferSize
      buf.skipBytes(toDiscard)
      buf.discardReadBytes()
    }
  }

  /**
   * Attempts to construct a joined game data packet. Returns null if not enough data is available
   * from all synced players.
   */
  fun getJoinedData(actionsPerMessage: Int, bytesPerAction: Int): ByteBuf? {
    val totalActionBytes = actionsPerMessage * bytesPerAction
    // Check readiness
    for (i in 0 until numPlayers) {
      if (synced[i]) {
        if (playerBuffers[i].readableBytes() < totalActionBytes) {
          return null
        }
      }
    }

    val totalSize = actionsPerMessage * numPlayers * bytesPerAction
    val joinedData = PooledByteBufAllocator.DEFAULT.buffer(totalSize)

    try {
      repeat(actionsPerMessage) {
        for (playerIndex in 0 until numPlayers) {
          if (synced[playerIndex]) {
            val buf = playerBuffers[playerIndex]
            // We read slice to avoid copy? Or just writeBytes?
            // writeBytes(buf, length) moves readerIndex!
            // Perfect, we want to consume it.
            joinedData.writeBytes(buf, bytesPerAction)
          } else {
            joinedData.writeZero(bytesPerAction)
          }
        }
      }

      for (i in 0 until numPlayers) {
        if (synced[i]) {
          // Start of buffer is readerIndex.
          playerBuffers[i].discardReadBytes()
        }
      }

      return joinedData
    } catch (e: Exception) {
      joinedData.release()
      throw e
    }
  }

  // For waitingOnPlayerNumber logic
  // Returns true if the player is synced but DOES NOT have enough data
  fun isWaitingOn(playerNumber: Int, requiredBytes: Int): Boolean {
    val index = playerNumber - 1
    if (index !in 0 until numPlayers) return false
    return synced[index] && playerBuffers[index].readableBytes() < requiredBytes
  }

  fun isSynced(playerNumber: Int): Boolean {
    val index = playerNumber - 1
    if (index !in 0 until numPlayers) return false
    return synced[index]
  }

  fun release() {
    // Release all buffers
    for (buf in playerBuffers) if (buf.refCnt() > 0) buf.release()
  }
}
