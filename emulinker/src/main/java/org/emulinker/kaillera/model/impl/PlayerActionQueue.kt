package org.emulinker.kaillera.model.impl

import io.netty.buffer.ByteBuf
import org.emulinker.kaillera.model.KailleraUser

/**
 * A buffer of game data for one player.
 *
 * Separately remembers how far through the buffer each player in the game has consumed.
 *
 * Not threadsafe.
 */
class PlayerActionQueue(
  // TODO(nue): We probably don't need this.
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
) {
  var lastTimeout: PlayerTimeoutException? = null

  private val data = ByteArray(gameBufferSize)

  /** Effectively a map of `player number - 1` to the last read index. */
  private val heads = IntArray(numPlayers)
  private var tail = 0

  /**
   * Whether the queue is synced with the [KailleraGame].
   *
   * Synced starts as `true` at the beginning of a game, and if it ever is set to false there is no
   * path where it will resync.
   */
  var synced = false
    private set

  fun markSynced() {
    synced = true
  }

  fun markDesynced() {
    synced = false
  }

  /** Adds "actions" at the [tail] position, and increments [tail]. */
  fun addActions(actions: ByteBuf) {
    if (!synced) return

    val length = actions.readableBytes()
    if (tail + length <= gameBufferSize) {
      // This can be done in one pass.
      actions.getBytes(actions.readerIndex(), data, tail, length)
    } else {
      // this has to be done in two steps because the array wraps around.
      val initialReadSize = gameBufferSize - tail
      actions.getBytes(actions.readerIndex(), data, tail, initialReadSize)
      actions.getBytes(actions.readerIndex() + initialReadSize, data, 0, length - initialReadSize)
    }
    tail = (tail + length) % gameBufferSize
    lastTimeout = null
  }

  fun getActionAndWriteToArray(
    readingPlayerIndex: Int,
    writeTo: ByteBuf,
    writeAtIndex: Int,
    actionLength: Int,
  ) {
    when {
      !synced -> {
        // If the player is no longer synced (e.g. if they left the game), make sure the target
        // range is set to 0.
        // We need to write zeros to the ByteBuf at writeAtIndex

        // Ensure capacity? ByteBuf should be large enough based on usage in KailleraGame.
        // We use setZero or just writeBytes(zeroArray).
        // Since we need to write at specific index, we use setBytes.
        // Or writeBytes if we are respecting the writerIndex?
        // KailleraGame calculates writeAtIndex -> Random Access.
        // So we use setBytes/setZero functions.
        writeTo.setZero(writeAtIndex, actionLength)
      }

      containsNewDataForPlayer(readingPlayerIndex, actionLength) -> {
        val head = heads[readingPlayerIndex]
        copyTo(writeTo, writeAtIndex, readStartIndex = head, readLength = actionLength)
        heads[readingPlayerIndex] = (head + actionLength) % gameBufferSize
      }

      else -> throw IllegalStateException("There is no data available for this synced user!")
    }
  }

  private fun copyTo(
    writeTo: ByteBuf,
    writeAtIndex: Int,
    readStartIndex: Int,
    readLength: Int,
  ) {
    if (readStartIndex + readLength <= gameBufferSize) {
      // This can be done in one pass.
      writeTo.setBytes(writeAtIndex, data, readStartIndex, readLength)
      return
    }

    // this has to be done in two steps because the array wraps around.
    val initialReadSize = gameBufferSize - readStartIndex

    writeTo.setBytes(writeAtIndex, data, readStartIndex, initialReadSize)

    writeTo.setBytes(writeAtIndex + initialReadSize, data, 0, readLength - initialReadSize)
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int) =
    getSize(playerIndex) >= actionLength

  /** Number of remaining bytes for the user to read. */
  private fun getSize(playerIndex: Int): Int =
    (tail + gameBufferSize - heads[playerIndex]) % gameBufferSize
}
