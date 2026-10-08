package org.emulinker.kaillera.model.impl

import io.netty.buffer.ByteBuf

/**
 * A buffer of game data for one player.
 *
 * Every player in the game reads this player's actions at their own pace, so the queue remembers
 * how far each reader has consumed. Bytes are copied into a fixed-size ring buffer: this runs once
 * per player per frame, so it deliberately avoids per-frame allocation and reference counting.
 *
 * If a reader falls more than [gameBufferSize] bytes behind, the oldest bytes it has not read yet
 * are dropped.
 *
 * Not threadsafe.
 */
class PlayerActionQueue(
  val playerNumber: Int,
  numPlayers: Int,
  gameBufferSize: Int,
) {
  private val capacity = gameBufferSize.coerceAtLeast(1)
  private val ring = ByteArray(capacity)

  /** Index in [ring] where the next byte will be written. */
  private var writePos = 0

  /** Per reader: index in [ring] of the next unread byte. */
  private val readPos = IntArray(numPlayers)

  /** Per reader: number of unread bytes. Never exceeds [capacity]. */
  private val unread = IntArray(numPlayers)

  /**
   * Whether the queue is synced with the [org.emulinker.kaillera.model.KailleraGame].
   *
   * Starts as `false`. It becomes `true` in [markSynced] at the beginning of a game, and if it ever
   * is set to false there is no path where it will resync.
   */
  var synced = false
    private set

  fun markSynced() {
    synced = true
    writePos = 0
    readPos.fill(0)
    unread.fill(0)
  }

  fun markDesynced() {
    synced = false
  }

  /** Adds "actions" to the queue. The caller keeps ownership of [actions]. */
  fun addActions(actions: ByteBuf) {
    if (!synced) {
      return
    }

    val length = actions.readableBytes()
    // Only the newest [capacity] bytes can possibly be kept.
    val toCopy = minOf(length, capacity)
    val sourceIndex = actions.readerIndex() + length - toCopy
    val firstStart = Math.floorMod(writePos + length - toCopy, capacity)
    val firstPart = minOf(toCopy, capacity - firstStart)
    actions.getBytes(sourceIndex, ring, firstStart, firstPart)
    if (firstPart < toCopy) {
      actions.getBytes(sourceIndex + firstPart, ring, 0, toCopy - firstPart)
    }
    writePos = Math.floorMod(writePos + length, capacity)

    for (i in unread.indices) {
      val total = unread[i] + length
      if (total > capacity) {
        // This reader fell too far behind: skip ahead to the oldest byte we still have.
        unread[i] = capacity
        readPos[i] = writePos
      } else {
        unread[i] = total
      }
    }
  }

  /**
   * Writes the next [actionLength] bytes for [readingPlayerIndex] to [writeTo], or zeroes if this
   * queue is not synced.
   */
  fun getActionAndWriteToArray(readingPlayerIndex: Int, writeTo: ByteBuf, actionLength: Int) {
    if (!synced) {
      writeTo.writeZero(actionLength)
      return
    }

    check(containsNewDataForPlayer(readingPlayerIndex, actionLength)) {
      "There is no data available for this synced user!"
    }

    val start = readPos[readingPlayerIndex]
    val firstPart = minOf(actionLength, capacity - start)
    writeTo.writeBytes(ring, start, firstPart)
    if (firstPart < actionLength) {
      writeTo.writeBytes(ring, 0, actionLength - firstPart)
    }
    readPos[readingPlayerIndex] = (start + actionLength) % capacity
    unread[readingPlayerIndex] -= actionLength
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int): Boolean =
    unread[playerIndex] >= actionLength
}
