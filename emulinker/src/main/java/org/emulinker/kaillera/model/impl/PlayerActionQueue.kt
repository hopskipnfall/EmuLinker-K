package org.emulinker.kaillera.model.impl

import kotlin.Throws
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.VariableSizeByteArray

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
   * Whether the queue is synced with the [KailleraGameImpl].
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
  fun addActions(actions: VariableSizeByteArray) {
    if (!synced) return

    if (tail + actions.size <= gameBufferSize) {
      // This can be done in one pass.
      actions.writeDataOutTo(copyTo = data, writeAtIndex = tail, 0, actions.size)
    } else {
      // this has to be done in two steps because the array wraps around.
      val initialReadSize = gameBufferSize - tail
      actions.writeDataOutTo(
        copyTo = data,
        writeAtIndex = tail,
        srcIndex = 0,
        // Read the remaining bytes until the end.
        writeLength = initialReadSize,
      )
      actions.writeDataOutTo(
        copyTo = data,
        writeAtIndex = 0,
        srcIndex = initialReadSize,
        // Read the remaining bytes until the end.
        writeLength = actions.size - initialReadSize,
      )
    }
    tail = (tail + actions.size) % gameBufferSize
    lastTimeout = null
  }

  @Throws(PlayerTimeoutException::class)
  fun getActionAndWriteToArray(
    readingPlayerIndex: Int,
    writeTo: VariableSizeByteArray,
    writeAtIndex: Int,
    actionLength: Int,
  ) {
    if (synced && !containsNewDataForPlayer(readingPlayerIndex, actionLength)) {
      throw AssertionError("I think this is impossible")
    }
    if (getSize(readingPlayerIndex) >= actionLength) {
      val head = heads[readingPlayerIndex]
      copyTo(writeTo, writeAtIndex, readStartIndex = head, readLength = actionLength)
      heads[readingPlayerIndex] = (head + actionLength) % gameBufferSize
      return
    }
    if (!synced) {
      // If the player is no longer synced (e.g. if they left the game), make sure the target range
      // is set to 0.
      writeTo.setZeroesForRange(
        fromIndex = writeAtIndex,
        untilIndexExclusive = writeAtIndex + actionLength,
      )
      return
    }
    throw PlayerTimeoutException(this.playerNumber, timeoutNumber = -1, player)
  }

  private fun copyTo(
    writeTo: VariableSizeByteArray,
    writeAtIndex: Int,
    readStartIndex: Int,
    readLength: Int,
  ) {
    if (readStartIndex + readLength <= gameBufferSize) {
      // This can be done in one pass.
      writeTo.importDataFrom(data, writeAtIndex, readStartIndex, readLength)
      return
    }

    // this has to be done in two steps because the array wraps around.
    val initialReadSize = gameBufferSize - readStartIndex
    writeTo.importDataFrom(
      copyFrom = data,
      writeAtIndex,
      readStartIndex,
      // Read the remaining bytes until the end.
      readLength = initialReadSize,
    )
    writeTo.importDataFrom(
      data,
      writeAtIndex = writeAtIndex + initialReadSize,
      readStartIndex = 0,
      // Read the remaining bytes until the end.
      readLength = readLength - initialReadSize,
    )
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int) =
    getSize(playerIndex) >= actionLength

  /** Number of remaining bytes for the user to read. */
  private fun getSize(playerIndex: Int): Int =
    (tail + gameBufferSize - heads[playerIndex]) % gameBufferSize
}
