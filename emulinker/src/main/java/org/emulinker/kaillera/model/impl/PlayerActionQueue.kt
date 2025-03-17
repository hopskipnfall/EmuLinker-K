package org.emulinker.kaillera.model.impl

import kotlin.Throws
import org.emulinker.kaillera.model.KailleraUser

class PlayerActionQueue(
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
) {
  var lastTimeout: PlayerTimeoutException? = null
  private val array = ByteArray(gameBufferSize)
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

  fun addActions(actions: ByteArray) {
    if (!synced) return
    for (i in actions.indices) {
      array[tail] = actions[i]
      // tail = ((tail + 1) % gameBufferSize);
      tail++
      if (tail == gameBufferSize) tail = 0
    }
    lastTimeout = null
  }

  @Throws(PlayerTimeoutException::class)
  fun getActionAndWriteToArray(
    playerIndex: Int,
    writeToArray: ByteArray,
    writeAtIndex: Int,
    actionLength: Int,
  ) {
    if (synced && !containsNewDataForPlayer(playerIndex, actionLength)) {
      throw AssertionError("I think this is impossible")
    }
    if (getSize(playerIndex) >= actionLength) {
      for (i in 0 until actionLength) {
        writeToArray[writeAtIndex + i] = array[heads[playerIndex]]
        // heads[playerIndex] = ((heads[playerIndex] + 1) % gameBufferSize);
        heads[playerIndex]++
        if (heads[playerIndex] == gameBufferSize) heads[playerIndex] = 0
      }
      return
    }
    if (!synced) return
    throw PlayerTimeoutException(this.playerNumber, timeoutNumber = -1, player)
  }

  fun containsNewDataForPlayer(playerIndex: Int, actionLength: Int) =
    getSize(playerIndex) >= actionLength

  private fun getSize(playerIndex: Int): Int =
    (tail + gameBufferSize - heads[playerIndex]) % gameBufferSize
}
