package org.emulinker.kaillera.model.impl

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.emulinker.testing.LoggingRule
import org.junit.Rule
import org.junit.Test

class GameActionQueueTest {
  @get:Rule val logging = LoggingRule()

  @Test
  fun `getJoinedData returns null if no data`() {
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    queue.markSynced(2)

    assertThat(queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)).isNull()
  }

  @Test
  fun `getJoinedData returns null if one player has insufficient data`() {
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    queue.markSynced(2)

    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(1, 2)))
    // Player 2 has no data

    assertThat(queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)).isNull()
  }

  @Test
  fun `getJoinedData returns joined data when all synced players are ready`() {
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    queue.markSynced(2)

    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(0xAA.toByte(), 0xBB.toByte())))
    queue.addActions(2, Unpooled.wrappedBuffer(byteArrayOf(0xCC.toByte(), 0xDD.toByte())))

    val joined = queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)
    assertThat(joined).isNotNull()

    // Expected: P1 data then P2 data
    val expected = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
    try {
      assertThat(ByteBufUtil.getBytes(joined)).isEqualTo(expected)
    } finally {
      joined?.release()
    }
  }

  @Test
  fun `getJoinedData interleaves actions`() {
    // 2 Actions per message, 2 bytes per action, 2 players
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    queue.markSynced(2)

    // P1: AABB, EEFF
    queue.addActions(
      1,
      Unpooled.wrappedBuffer(
        byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xEE.toByte(), 0xFF.toByte())
      ),
    )
    // P2: CCDD, 0011
    queue.addActions(
      2,
      Unpooled.wrappedBuffer(
        byteArrayOf(0xCC.toByte(), 0xDD.toByte(), 0x00.toByte(), 0x11.toByte())
      ),
    )

    val joined = queue.getJoinedData(actionsPerMessage = 2, bytesPerAction = 2)
    assertThat(joined).isNotNull()

    // Expected: Action 1 (P1, P2) then Action 2 (P1, P2)
    val expected =
      byteArrayOf(
        0xAA.toByte(),
        0xBB.toByte(),
        0xCC.toByte(),
        0xDD.toByte(), // Action 1
        0xEE.toByte(),
        0xFF.toByte(),
        0x00.toByte(),
        0x11.toByte(), // Action 2
      )

    try {
      assertThat(ByteBufUtil.getBytes(joined)).isEqualTo(expected)
    } finally {
      joined?.release()
    }
  }

  @Test
  fun `getJoinedData uses zeroes for desynced players`() {
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    // Player 2 is NOT synced

    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(0xAA.toByte(), 0xBB.toByte())))

    val joined = queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)
    assertThat(joined).isNotNull()

    // Expected: P1 data then 0000 for P2
    val expected = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x00, 0x00)
    try {
      assertThat(ByteBufUtil.getBytes(joined)).isEqualTo(expected)
    } finally {
      joined?.release()
    }
  }

  @Test
  fun `getJoinedData consumes data`() {
    val queue = GameActionQueue(numPlayers = 1, bufferSize = 4096)
    queue.markSynced(1)

    // Add 4 bytes
    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4)))

    // Consume 2 bytes
    var joined = queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)
    assertThat(joined).isNotNull()
    joined?.release()

    // Should have 2 bytes left
    assertThat(queue.isWaitingOn(1, 2)).isFalse()

    // Consume remaining 2 bytes
    joined = queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)
    assertThat(joined).isNotNull()
    joined?.release()

    // Should be empty now
    assertThat(queue.isWaitingOn(1, 2)).isTrue()
    assertThat(queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 2)).isNull()
  }

  @Test
  fun `markSynced clears buffer`() {
    val queue = GameActionQueue(numPlayers = 1, bufferSize = 4096)
    queue.markSynced(1)
    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))

    queue.markSynced(1) // Should clear buffer

    assertThat(queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 1)).isNull()
    assertThat(queue.isWaitingOn(1, 1)).isTrue()
  }

  @Test
  fun `markDesynced clears buffer`() {
    val queue = GameActionQueue(numPlayers = 1, bufferSize = 4096)
    queue.markSynced(1)
    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))

    queue.markDesynced(1) // Should clear buffer and mark desynced

    assertThat(queue.isSynced(1)).isFalse()
    // Even if we mark it synced again, it should be empty
    queue.markSynced(1)
    assertThat(queue.getJoinedData(actionsPerMessage = 1, bytesPerAction = 1)).isNull()
  }

  @Test
  fun `isWaitingOn works correctly`() {
    val queue = GameActionQueue(numPlayers = 2, bufferSize = 4096)
    queue.markSynced(1)
    queue.markSynced(2)

    queue.addActions(1, Unpooled.wrappedBuffer(byteArrayOf(1, 2)))

    // P1 has data (2 bytes), P2 has 0.
    assertThat(queue.isWaitingOn(1, 2)).isFalse() // Has >= 2 bytes
    assertThat(queue.isWaitingOn(2, 2)).isTrue() // Has < 2 bytes

    // Asking for more than P1 has
    assertThat(queue.isWaitingOn(1, 4)).isTrue()

    // Desynced player is never "waiting on"
    queue.markDesynced(2)
    assertThat(queue.isWaitingOn(2, 2)).isFalse()
  }

  @Test
  fun `release frees resources`() {
    val queue = GameActionQueue(numPlayers = 1, bufferSize = 4096)
    val buf = Unpooled.buffer(10)
    buf.writeBytes(byteArrayOf(1, 2, 3))
    assertThat(buf.refCnt()).isEqualTo(1)

    queue.markSynced(1)
    queue.addActions(1, buf) // Retains buf, refCnt 2

    queue.release()

    assertThat(buf.refCnt()).isEqualTo(1)
    buf.release()
    assertThat(buf.refCnt()).isEqualTo(0)
  }
}
