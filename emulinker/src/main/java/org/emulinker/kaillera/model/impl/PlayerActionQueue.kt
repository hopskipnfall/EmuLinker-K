package org.emulinker.kaillera.model.impl

import kotlin.Throws
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.emulinker.kaillera.model.KailleraUser

class PlayerActionQueue(
  val playerNumber: Int,
  val player: KailleraUser,
  numPlayers: Int,
  private val gameBufferSize: Int,
  private val gameTimeoutMillis: Int,
  capture: Boolean
) {
  var lastTimeout: PlayerTimeoutException? = null
  private val array = ByteArray(gameBufferSize)
  private val heads = IntArray(numPlayers)
  private var tail = 0

  private val mutex = Mutex()
  private val syncedEventChannel = Channel<Boolean>(1_000)

  var synched = false
    private set

  suspend fun setSynched(newval: Boolean) {
    mutex.withLock {
      synched = newval
      // TODO(nue): Why is this negated??
      if (!newval) {
        syncedEventChannel.send(true)
      }
    }
  }

  suspend fun addActions(actions: ByteArray) {
    if (!synched) return
    for (i in actions.indices) {
      array[tail] = actions[i]
      // tail = ((tail + 1) % gameBufferSize);
      tail++
      if (tail == gameBufferSize) tail = 0
    }
    mutex.withLock { syncedEventChannel.send(true) }
    lastTimeout = null
  }

  @Throws(PlayerTimeoutException::class)
  suspend fun getAction(playerNumber: Int, actions: ByteArray, location: Int, actionLength: Int) {
    // TODO(nue): We definitely shouldn't be locking here. Maybe we should use a mutex.
    mutex.withLock {
      if (getSize(playerNumber) < actionLength && synched) {
        // Wait for the game to be synced.
        withTimeoutOrNull(gameTimeoutMillis.milliseconds) { syncedEventChannel.receive() }
      }
    }
    if (getSize(playerNumber) >= actionLength) {
      for (i in 0 until actionLength) {
        actions[location + i] = array[heads[playerNumber - 1]]
        // heads[(playerNumber - 1)] = ((heads[(playerNumber - 1)] + 1) % gameBufferSize);
        heads[playerNumber - 1]++
        if (heads[playerNumber - 1] == gameBufferSize) heads[playerNumber - 1] = 0
      }
      return
    }
    if (!synched) return
    throw PlayerTimeoutException(this.playerNumber, /* timeoutNumber= */ -1, player)
  }

  private fun getSize(playerNumber: Int): Int {
    return (tail + gameBufferSize - heads[playerNumber - 1]) % gameBufferSize
  }
}
