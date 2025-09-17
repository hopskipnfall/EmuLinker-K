package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
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
  fun zeroesForPlayerDesynched() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
        player = mock(),
        numPlayers = 1,
        // Something slightly bigger than DATA.size so it will wrap around if we add two.
        gameBufferSize = DATA.size + 5,
      )
    queue.markDesynced()

    queue.addActions(VariableSizeByteArray(DATA))

    val out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(ByteArray(DATA.size) { 0x00 })
  }

  @Test
  fun containsNewDataForPlayer() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
        player = mock(),
        numPlayers = 1,
        // Something slightly bigger than DATA.size so it will wrap around if we add two.
        gameBufferSize = DATA.size + 5,
      )
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    val out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)
  }

  @Test
  fun `containsNewDataForPlayer handles wrap-around`() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
        player = mock(),
        numPlayers = 1,
        // Something slightly bigger than DATA.size so it will wrap around if we add two.
        gameBufferSize = DATA.size + 5,
      )
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    var out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)

    queue.addActions(VariableSizeByteArray(DATA))

    out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)
  }

  @Test
  fun `containsNewDataForPlayer handles wrap-around with an even multiple size`() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
        player = mock(),
        numPlayers = 1,
        gameBufferSize = DATA.size * 2,
      )
    queue.markSynced()

    queue.addActions(VariableSizeByteArray(DATA))

    var out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)

    queue.addActions(VariableSizeByteArray(DATA))

    out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(out.toByteArray()).isEqualTo(DATA)

    queue.addActions(VariableSizeByteArray(DATA))

    out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
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

    val out = VariableSizeByteArray(Random.nextBytes(DATA.size))
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
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
