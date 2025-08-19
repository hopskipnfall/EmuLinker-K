package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import org.emulinker.testing.LoggingRule
import org.emulinker.util.VariableSizeByteArray
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class PlayerActionQueueTest {
  @get:Rule val logging = LoggingRule()

  @Test
  fun `containsNewDataForPlayer returns false if no data`() {
    val queue =
      PlayerActionQueue(playerNumber = 1, player = mock(), numPlayers = 1, gameBufferSize = 4096)
    queue.markSynced()

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isFalse()
  }

  @Test
  fun `containsNewDataForPlayer returns false if there is data`() {
    val queue =
      PlayerActionQueue(playerNumber = 1, player = mock(), numPlayers = 1, gameBufferSize = 4096)
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isTrue()
  }

  @Test
  fun containsNewDataForPlayer() {
    val queue =
      PlayerActionQueue(playerNumber = 1, player = mock(), numPlayers = 1, gameBufferSize = 4096)
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    val out = VariableSizeByteArray(ByteArray(DATA.size) { 0 })
    queue.getActionAndWriteToArray(
      playerIndex = 0,
      writeToArray = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)
  }

  @Test
  @Ignore // TODO: Fill out this test properly with real data.
  fun `containsNewDataForPlayer works for two players`() {
    val queue =
      PlayerActionQueue(playerNumber = 1, player = mock(), numPlayers = 2, gameBufferSize = 4096)
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    val out = VariableSizeByteArray(ByteArray(DATA.size) { 0 })
    queue.getActionAndWriteToArray(
      playerIndex = 0,
      writeToArray = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isTrue()
    assertThat(out.toByteArray()).isEqualTo(DATA)
  }

  companion object {
    val DATA = byteArrayOf(16, 32, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  }
}
