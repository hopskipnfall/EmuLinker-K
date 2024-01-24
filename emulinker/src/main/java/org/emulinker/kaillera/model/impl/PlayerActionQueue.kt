package org.emulinker.kaillera.model.impl

import com.google.common.flogger.FluentLogger
import java.util.concurrent.TimeUnit
import kotlin.Throws
import kotlin.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.SuspendUntilSignaled

class PlayerActionQueue(
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
  private val gameTimeout: Duration
) {
  var lastTimeout: PlayerTimeoutException? = null
  private val array = ByteArray(gameBufferSize)
  private val heads = IntArray(numPlayers)
  private var tail = 0

  private val mutex = Mutex()
  private val suspender = SuspendUntilSignaled()

  /**
   * Synced starts as `true` at the beginning of a game, and if it ever is set to false there is no
   * path where it will resync.
   */
  var synced = false
    private set

  fun markSynced() {
    synced = true
  }

  suspend fun markDesynced() {
    synced = false
    // The game is in a broken state, so we should wake up all coroutines blocked waiting for new
    // data.
    suspender.signalAll()
  }

  suspend fun addActions(actions: ByteArray) {
    if (!synced) return
    for (actionByte in actions) {
      array[tail] = actionByte
      // tail = ((tail + 1) % gameBufferSize);
      tail++
      if (tail == gameBufferSize) tail = 0
    }
    suspender.signalAll()
    lastTimeout = null
  }

  /** Writes data to the array [writeToArray] starting at index [writeAtIndex]. */
  @Throws(PlayerTimeoutException::class) // TODO(nue): Return Result<Unit>.
  suspend fun getActionAndWriteToArray(
    playerIndex: Int,
    writeToArray: ByteArray,
    writeAtIndex: Int,
    actionLength: Int
  ) {
    mutex.withLock {
      if (synced && !containsNewDataForPlayer(playerIndex, actionLength)) {
        withTimeoutOrNull(gameTimeout) { suspender.suspendUntilSignaled() }
          ?: logger
            .atSevere()
            .atMostEvery(10, TimeUnit.SECONDS)
            .log("Timed out while waiting to be synced.")
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

  private companion object {
    val logger = FluentLogger.forEnclosingClass()
  }
}
