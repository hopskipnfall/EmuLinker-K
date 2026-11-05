package org.emulinker.kaillera.model.impl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import kotlin.random.Random
import org.emulinker.testing.LoggingListener

class PlayerActionQueueTest :
  FunSpec({
    extension(LoggingListener())

    test("containsNewDataForPlayer returns false if no data") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4096)
      queue.markSynced()

      queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size).shouldBeFalse()
    }

    test("containsNewDataForPlayer returns false if there is data") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4096)
      queue.markSynced()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      queue.containsNewDataForPlayer(playerIndex = 0, actionLength = DATA.size).shouldBeTrue()
    }

    test("zeroesForPlayerDesynched") {
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
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      // Verify out contains zeroes
      val zeroes = ByteArray(DATA.size)
      val actual = ByteArray(DATA.size)
      out.getBytes(0, actual)
      actual shouldBe zeroes
    }

    test("containsNewDataForPlayer") {
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
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()
    }

    test("containsNewDataForPlayer handles wrap-around") {
      val queue =
        PlayerActionQueue(
          playerNumber = 1,
          numPlayers = 1,
          gameBufferSize = DATA.size + 5,
        )
      queue.markSynced()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      var out = Unpooled.buffer(DATA.size)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      out = Unpooled.buffer(DATA.size)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()
    }

    test("containsNewDataForPlayer handles wrap-around with an even multiple size") {
      val queue =
        PlayerActionQueue(
          playerNumber = 1,
          numPlayers = 1,
          gameBufferSize = DATA.size * 2,
        )
      queue.markSynced()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      var out = Unpooled.buffer(DATA.size)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      out = Unpooled.buffer(DATA.size)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()

      queue.addActions(Unpooled.wrappedBuffer(DATA))

      out = Unpooled.buffer(DATA.size)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = 0,
        writeTo = out,
        actionLength = DATA.size,
      )

      ByteBufUtil.equals(out, Unpooled.wrappedBuffer(DATA)).shouldBeTrue()
    }

    test("each reader consumes independently") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 2, gameBufferSize = 64)
      queue.markSynced()
      queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4)))

      read(queue, 0, 2) shouldBe byteArrayOf(1, 2)
      queue.containsNewDataForPlayer(0, 3).shouldBeFalse()
      queue.containsNewDataForPlayer(1, 4).shouldBeTrue()
      read(queue, 1, 4) shouldBe byteArrayOf(1, 2, 3, 4)
      read(queue, 0, 2) shouldBe byteArrayOf(3, 4)
    }

    test("a reader that falls behind loses the oldest bytes") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4)
      queue.markSynced()

      queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
      queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(4, 5, 6)))

      read(queue, 0, 4) shouldBe byteArrayOf(3, 4, 5, 6)
      queue.containsNewDataForPlayer(0, 1).shouldBeFalse()
    }

    test("a single write larger than the buffer keeps only the newest bytes") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 4)
      queue.markSynced()

      queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4, 5, 6)))

      read(queue, 0, 4) shouldBe byteArrayOf(3, 4, 5, 6)
    }

    test("reading with no data available throws") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 8)
      queue.markSynced()

      shouldThrow<IllegalStateException> { read(queue, 0, 1) }
    }

    test("markSynced discards data from the previous game") {
      val queue = PlayerActionQueue(playerNumber = 1, numPlayers = 1, gameBufferSize = 8)
      queue.markSynced()
      queue.addActions(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))

      queue.markSynced()

      queue.containsNewDataForPlayer(0, 1).shouldBeFalse()
    }

    // Compares the ring buffer against a trivial list-based model over random operations.
    test("behaves like a simple reference model under random reads and writes") {
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
            queue.containsNewDataForPlayer(reader, length) shouldBe (model[reader].size >= length)
            if (model[reader].size >= length) {
              val expected = ByteArray(length) { model[reader].removeFirst() }
              read(queue, reader, length) shouldBe expected
            }
          }
        }
      }
    }
  }) {
  companion object {
    val DATA = byteArrayOf(16, 32, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private fun read(queue: PlayerActionQueue, reader: Int, length: Int): ByteArray {
      val out = Unpooled.buffer(length)
      queue.getActionAndWriteToArray(
        readingPlayerIndex = reader,
        writeTo = out,
        actionLength = length,
      )
      return ByteArray(length).also { out.getBytes(0, it) }
    }
  }
}
