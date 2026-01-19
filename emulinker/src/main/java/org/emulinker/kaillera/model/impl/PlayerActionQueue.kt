package org.emulinker.kaillera.model.impl

/**
 * A buffer of game data for one player.
 *
 * Separately remembers how far through the buffer each player in the game has consumed.
 *
 * Not threadsafe.
 */
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import org.emulinker.kaillera.model.KailleraUser

class PlayerActionQueue(
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
) {
  var lastTimeout: PlayerTimeoutException? = null

  private val data: CompositeByteBuf = Unpooled.compositeBuffer()

  // Total bytes currently available to read from the start of the buffer
  private var totalWrittenBytes = 0

  // How many bytes this specific player has read from the stream
  private var readPosition = 0

  /**
   * Whether the queue is synced with the [org.emulinker.kaillera.model.KailleraGame].
   *
   * Synced starts as `true` at the beginning of a game, and if it ever is set to false there is no
   * path where it will resync.
   */
  var synced = false
    private set

  fun markSynced() {
    synced = true
    if (data.numComponents() > 0) {
      data.removeComponents(0, data.numComponents())
    }
    data.clear()
    totalWrittenBytes = 0
    readPosition = 0
  }

  fun markDesynced() {
    synced = false
    // TODO(nue): See if this is the correct way to do this. Maybe there is a function to throw away
    // the rest of the bytes?
    if (data.refCnt() > 0) data.release()
  }

  /** Adds "actions" to the queue. */
  fun addActions(actions: ByteBuf) {
    if (!synced) {
      return
    }

    data.addComponent(true, actions.retain())
    totalWrittenBytes += actions.readableBytes()

    if (data.readableBytes() > gameBufferSize) {
      // Discard bytes from the beginning
      val toDiscard = data.readableBytes() - gameBufferSize
      data.skipBytes(toDiscard)
      data.discardReadBytes()

      for (i in heads.indices) {
        heads[i] = (heads[i] - toDiscard).coerceAtLeast(0)
      }
    }

    lastTimeout = null
  }

  private val heads = IntArray(numPlayers)

  fun getActionAndWriteToArray(readingPlayerIndex: Int, writeTo: ByteBuf, actionLength: Int) {
    if (!synced) {
      writeTo.writeZero(actionLength)
      return
    }

    if (containsNewDataForPlayer(readingPlayerIndex, actionLength)) {
      val relativeHead = heads[readingPlayerIndex]

      if (relativeHead + actionLength > data.writerIndex()) {
        // Should verify containsNewDataForPlayer check coverage
        throw IllegalStateException("Not enough data!")
      }

      writeTo.writeBytes(data, relativeHead, actionLength)

      heads[readingPlayerIndex] += actionLength

      cleanUp()
    } else {
      throw IllegalStateException("There is no data available for this synced user!")
    }
  }

  private fun cleanUp() {
    // Find the minimum head. We can discard data before that.
    var minHead = Int.MAX_VALUE
    for (h in heads) {
      if (h < minHead) minHead = h
    }

    if (minHead > 0) {
      data.readerIndex(minHead)
      data.discardReadBytes()
      for (i in heads.indices) {
        heads[i] -= minHead
      }
    }
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int): Boolean {
    val head = heads[playerIndex]
    val available = data.writerIndex() - head
    return available >= actionLength
  }
}
