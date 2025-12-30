package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import kotlin.random.Random
import org.emulinker.testing.LoggingRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class PlayerActionQueueTest {
  @get:Rule
  val logging = LoggingRule()

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

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)
    // We shouldn't release buf because addActions consumes/reads it but not retains it?
    // addActions reads from it. Unpooled.wrappedBuffer(DATA) creates wrapper.
    // We passed it. Logic in addActions reads it.
    // No release needed if we don't retain it locally?
    // Wait, Unpooled.wrappedBuffer returns a buffer with refCnt 1.
    // If addActions reads it and we are done with it, we should release it.
    // But check PlayerActionQueue implementation -> addActions uses getBytes. It does NOT retain.
    // So YES we must release it.
    buf.release()

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

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)
    buf.release()

    val out = Unpooled.buffer(DATA.size)
    // Make sure it has capacity
    out.writeZero(DATA.size) // Fill with something else? No, just ensure writable.
    out.clear() // Reset reader/writer

    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    val resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    out.release()

    assertThat(resultBytes).isEqualTo(ByteArray(DATA.size) { 0x00 })
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

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)
    buf.release()

    val out = Unpooled.buffer(DATA.size)

    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    val resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    out.release()

    assertThat(resultBytes).isEqualTo(DATA)
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

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)
    // buf wrapped DATA.
    // We should not release buf because we reuse DATA?
    // Unpooled.wrappedBuffer(DATA) wraps the array.
    // ByteBuf operations don't modify array content if we only getBytes.
    // Safe to reuse DATA array. But we need new ByteBuf wrapper each time we wrap or just retain?
    // If we release buf, the array is fine.

    // BUT we need to pass a valid buffer to addActions.

    var out = Unpooled.buffer(DATA.size)

    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    var resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()
    buf.release()

    // Add again
    val buf2 = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf2)
    buf2.release()

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()
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

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)

    var out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    var resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()

    // Add again
    // Reusing buf (assuming we didn't release it yet or reset reader index)
    buf.readerIndex(0)
    queue.addActions(buf)

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()

    // Add again
    buf.readerIndex(0)
    queue.addActions(buf)
    buf.release()

    out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()
  }

  @Test
  @Ignore // TODO: Fill out this test properly with real data.
  fun `containsNewDataForPlayer works for two players`() {
    val queue =
      PlayerActionQueue(playerNumber = 1, player = mock(), numPlayers = 2, gameBufferSize = 4096)
    queue.markSynced()

    val buf = Unpooled.wrappedBuffer(DATA)
    queue.addActions(buf)
    buf.release()

    val out = Unpooled.buffer(DATA.size)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = 0,
      writeTo = out,
      writeAtIndex = 0,
      actionLength = DATA.size,
    )

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isTrue()

    val resultBytes = ByteArray(DATA.size)
    out.getBytes(0, resultBytes)
    assertThat(resultBytes).isEqualTo(DATA)
    out.release()
  }

  companion object {
    val DATA = byteArrayOf(16, 32, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  }
}
