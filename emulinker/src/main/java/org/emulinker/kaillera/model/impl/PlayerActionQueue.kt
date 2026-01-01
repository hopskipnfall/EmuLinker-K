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

  // We use a CompositeByteBuf to hold the queue of data.
  // This avoids copying when adding data, we just add the component.
  // However, we need to be careful about releasing components when they are fully read.
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
    // Reset state?
    // data.clear() checks writerIndex/readerIndex but does not release components.
    // We must release components to avoid leaks.
    if (data.numComponents() > 0) {
      data.removeComponents(0, data.numComponents())
    }
    data.clear()
    totalWrittenBytes = 0
    readPosition = 0
  }

  fun markDesynced() {
    synced = false
    data.release()
  }

  /** Adds "actions" to the queue. */
  fun addActions(actions: ByteBuf) {
    if (!synced) {
      actions.release()
      return
    }

    data.addComponent(true, actions.retain())
    totalWrittenBytes += actions.readableBytes()

    if (data.readableBytes() > gameBufferSize) {
      // Discard bytes from the beginning
      val toDiscard = data.readableBytes() - gameBufferSize
      data.skipBytes(toDiscard)
      data.discardReadBytes()
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
      // We can discard `minHead` bytes.
      // data.discardReadBytes() works on readerIndex.
      // So we set readerIndex to minHead.
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
