package org.emulinker.kaillera.model.impl

import java.lang.InterruptedException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.Throws
import kotlin.concurrent.withLock
import kotlin.time.Duration
import org.emulinker.kaillera.model.KailleraUser

class PlayerActionQueue(
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
  private val gameTimeout: Duration,
) {
  var lastTimeout: PlayerTimeoutException? = null
  private val array = ByteArray(gameBufferSize)
  private val heads = IntArray(numPlayers)
  private var tail = 0

  /**
   * Synced starts as `true` at the beginning of a game, and if it ever is set to false there is no
   * path where it will resync.
   */
  var synced = false
    private set

  private val lock = ReentrantLock()
  private val condition = lock.newCondition()

  fun markSynced() {
    synced = true
  }

  fun markDesynced() {
    synced = false
    // The game is in a broken state, so we should wake up all threads blocked waiting for new data.
    lock.withLock { condition.signalAll() }
  }

  fun addActions(actions: ByteArray) {
    if (!synced) return
    for (i in actions.indices) {
      array[tail] = actions[i]
      // tail = ((tail + 1) % gameBufferSize);
      tail++
      if (tail == gameBufferSize) tail = 0
    }
    lock.withLock { condition.signalAll() }
    lastTimeout = null
  }

  @Throws(PlayerTimeoutException::class)
  fun getActionAndWriteToArray(
    playerIndex: Int,
    writeToArray: ByteArray,
    writeAtIndex: Int,
    actionLength: Int
  ) {
    // Note: It's possible this never happens and we can replace this with an assertion.
    lock.withLock {
      if (synced && !containsNewDataForPlayer(playerIndex, actionLength)) {
        try {
          condition.await(gameTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {}
      }
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
