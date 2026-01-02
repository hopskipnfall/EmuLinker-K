package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.emulinker.testing.LoggingRule
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

    queue.addActions(Unpooled.wrappedBuffer(DATA))

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

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    val out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    // Verify out contains zeroes
    val zeroes = ByteArray(DATA.size)
    val actual = ByteArray(DATA.size)
    out.getBytes(0, actual)
    assertThat(actual).isEqualTo(zeroes)
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

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    val out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()
  }

  @Test
  fun `containsNewDataForPlayer handles wrap-around`() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
        player = mock(),
        numPlayers = 1,
        gameBufferSize = DATA.size + 5,
      )
    queue.markSynced()

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    var out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()
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

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    var out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(readingPlayerIndex = 0, writeTo = out, actionLength = DATA.size)

    assertThat(ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA))).isTrue()
  }

  companion object {
    val DATA = byteArrayOf(16, 32, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  }
}
