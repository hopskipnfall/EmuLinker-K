package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import kotlin.random.Random
import org.emulinker.testing.LoggingRule
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class PlayerActionQueueTest {
  @get:Rule val logging = LoggingRule()

  @Test
  fun `containsNewDataForPlayer returns false if no data`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4096)
    queue.markSynced()

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isFalse()
  }

  @Test
  fun `containsNewDataForPlayer returns false if there is data`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4096)
    queue.markSynced()

    queue.addActions(Unpooled.wrappedBuffer(DATA))

    assertThat(queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size)).isTrue()
  }

  @Test
  fun zeroesForPlayerDesynched() {
    val queue =
      PlayerActionQueue(
        playerNumber = 1,
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

  @Test
  fun `each reader consumes independently`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 2, gameBufferSize = 64)
    queue.markSynced()
    queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4)))

    assertThat(read(queue, 0, 2)).isEqualTo(byteArrayOf(1, 2))
    assertThat(queue.containsNewDataForPlayer(0, 3)).isFalse()
    assertThat(queue.containsNewDataForPlayer(1, 4)).isTrue()
    assertThat(read(queue, 1, 4)).isEqualTo(byteArrayOf(1, 2, 3, 4))
    assertThat(read(queue, 0, 2)).isEqualTo(byteArrayOf(3, 4))
  }

  @Test
  fun `a reader that falls behind loses the oldest bytes`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4)
    queue.markSynced()

    queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
    queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(4, 5, 6)))

    assertThat(read(queue, 0, 4)).isEqualTo(byteArrayOf(3, 4, 5, 6))
    assertThat(queue.containsNewDataForPlayer(0, 1)).isFalse()
  }

  @Test
  fun `a single write larger than the buffer keeps only the newest bytes`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4)
    queue.markSynced()

    queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4, 5, 6)))

    assertThat(read(queue, 0, 4)).isEqualTo(byteArrayOf(3, 4, 5, 6))
  }

  @Test
  fun `reading with no data available throws`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 8)
    queue.markSynced()

    assertThrows(IllegalStateException::class.java) { read(queue, 0, 1) }
  }

  @Test
  fun `markSynced discards data from the previous game`() {
    val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 8)
    queue.markSynced()
    queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))

    queue.markSynced()

    assertThat(queue.containsNewDataForPlayer(0, 1)).isFalse()
  }

  /** Compares the ring buffer against a trivial list-based model over random operations. */
  @Test
  fun `behaves like a simple reference model under random reads and writes`() {
    val random = Random(1234)
    repeat(50) {
      val capacity = random.nextInt(1, 20)
      val readers = random.nextInt(1, 4)
      val queue =
        PlayerActionQueue(
          playerNumber = 1,
          numPlayers = readers,
          gameBufferSize = capacity,
        )
      queue.markSynced()
      val model = List(readers) { ArrayDeque<Byte>() }

      repeat(300) {
        if (random.nextBoolean()) {
          val bytes = ByteArray(random.nextInt(0, capacity + 4)) { random.nextInt().toByte() }
          queue.addActions(Unpooled.wrappedBuffer(bytes))
          for (m in model) {
            m.addAll(bytes.toList())
            while (m.size > capacity) m.removeFirst()
          }
        } else {
          val reader = random.nextInt(readers)
          val length = random.nextInt(1, capacity + 1)
          assertThat(queue.containsNewDataForPlayer(reader, length))
            .isEqualTo(model[reader].size >= length)
          if (model[reader].size >= length) {
            val expected = ByteArray(length) { model[reader].removeFirst() }
            assertThat(read(queue, reader, length)).isEqualTo(expected)
          }
        }
      }
    }
  }

  private fun read(queue: PlayerActionQueue, reader: Int, length: Int): ByteArray {
    val out = Unpooled.buffer(length)
    queue.getActionAndWriteToArray(
      readingPlayerIndex = reader,
      writeTo = out,
      actionLength = length,
    )
    return ByteArray(length).also { out.getBytes(0, it) }
  }

  companion object {
    val DATA = byteArrayOf(16, 32, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  }
}
