package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
import java.net.InetSocketAddress
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

@OptIn(kotlin.time.ExperimentalTime::class)
class KailleraGameTest {

  private val owner =
    mock<KailleraUser> {
      on { connectionType } doReturn ConnectionType.LAN
      on { clientType } doReturn "emulator"
      on { connectSocketAddress } doReturn InetSocketAddress("127.0.0.1", 12345)
      on { socketAddress } doReturn InetSocketAddress("127.0.0.1", 12345)
      on { name } doReturn "Owner"
      on { id } doReturn 1
    }
  private val accessManager =
    mock<AccessManager> { on { getAccess(any()) } doReturn AccessManager.ACCESS_NORMAL }
  private val autoFireDetector = mock<AutoFireDetector>()
  private val server =
    mock<KailleraServer> {
      on { accessManager } doReturn accessManager
      on { getAutoFireDetector(any()) } doReturn autoFireDetector
    }
  private val flags = mock<RuntimeFlags>()
  private val clock = mock<Clock> { on { now() } doReturn Clock.System.now() }

  private val game =
    KailleraGame(
      id = GAME_ID,
      romName = ROM_NAME,
      owner = owner,
      server = server,
      bufferSize = 100,
      flags = flags,
      clock = clock,
    )

  @Test
  fun `test initialization`() {
    assertThat(game.id).isEqualTo(GAME_ID)
    assertThat(game.romName).isEqualTo(ROM_NAME)
    assertThat(game.owner).isEqualTo(owner)
    assertThat(game.status).isEqualTo(GameStatus.WAITING)
  }

  @Test
  fun `test toString contains rom name`() {
    assertThat(game.toString()).contains(ROM_NAME)
    assertThat(game.toString()).contains(GAME_ID.toString())
  }

  @Test
  fun `join adds user to players`() {
    // Need to mock owner.game access for emulator check inside join()
    // The code checks: if (access < ELEVATED && aEmulator != "any") ...
    // and also: if (access < ADMIN && user.clientType != owner.clientType &&
    // !owner.game!!.romName.startsWith("*"))
    // owner.game is accessed.
    // We need to make sure owner returns the game mock/stub.

    // Since 'owner' is already created in setUp, we need to update it or make 'game' available to
    // it.
    // Mockito stubs are mutable.
    whenever(owner.game).thenReturn(game)

    val user =
      mock<KailleraUser> {
        on { connectSocketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { socketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { ping } doReturn 50.milliseconds
        on { clientType } doReturn "emulator"
        on { connectionType } doReturn ConnectionType.LAN
        on { name } doReturn "Player2"
        on { id } doReturn 2
      }

    val playerNumber = game.join(user)

    assertThat(game.players).contains(user)
    assertThat(playerNumber).isEqualTo(1)
  }

  @Test
  fun `join fails if game is full`() {
    game.maxUsers = 1

    whenever(owner.game).thenReturn(game)

    val user1 =
      mock<KailleraUser> {
        on { connectSocketAddress } doReturn InetSocketAddress("1.1.1.1", 1111)
        on { socketAddress } doReturn InetSocketAddress("1.1.1.1", 1111)
        on { ping } doReturn 10.milliseconds
        on { clientType } doReturn "emulator"
        on { connectionType } doReturn ConnectionType.LAN
        on { id } doReturn 10
      }

    game.join(user1)

    val user2 =
      mock<KailleraUser> {
        on { connectSocketAddress } doReturn InetSocketAddress("2.2.2.2", 2222)
        on { socketAddress } doReturn InetSocketAddress("2.2.2.2", 2222)
        on { id } doReturn 20
      }

    assertFailsWith<JoinGameException> {game.join(user2)}
  }

  @Test
  fun `start game successfully`() {
    whenever(owner.game).thenReturn(game)
    whenever(owner.inStealthMode).thenReturn(false)
    whenever(owner.ping).thenReturn(10.milliseconds)

    game.join(owner)

    whenever(flags.allowSinglePlayer).thenReturn(true)

    game.start(owner)

    assertThat(game.status).isEqualTo(GameStatus.SYNCHRONIZING)
    verify(server, atLeastOnce()).addEvent(any())
  }

  @Test
  fun `start game fails if not owner`() {
    whenever(owner.game).thenReturn(game)

    val user =
      mock<KailleraUser> {
        on { connectSocketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { socketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { ping } doReturn 10.milliseconds
        on { clientType } doReturn "emulator"
        on { connectionType } doReturn ConnectionType.LAN
        on { id } doReturn 2
      }

    game.join(user)

    assertFailsWith<StartGameException> { game.start(user) }
  }

  @Test
  fun `kick removes user`() {
    whenever(owner.game).thenReturn(game)
    game.join(owner)

    val user =
      mock<KailleraUser> {
        on { connectSocketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { socketAddress } doReturn InetSocketAddress("1.2.3.4", 5555)
        on { ping } doReturn 10.milliseconds
        on { clientType } doReturn "emulator"
        on { connectionType } doReturn ConnectionType.LAN
        on { id } doReturn 2
        on { accessLevel } doReturn AccessManager.ACCESS_NORMAL
      }

    whenever(user.quitGame()).thenAnswer { game.quit(user, 2) }

    game.join(user)

    game.kick(owner, 2)

    verify(user).quitGame()
  }

  @Test
  fun `ready updates status when all players ready`() {
    whenever(owner.game).thenReturn(game)
    whenever(owner.inStealthMode).thenReturn(false)
    whenever(owner.ping).thenReturn(10.milliseconds)
    whenever(flags.allowSinglePlayer).thenReturn(true)

    game.join(owner)
    game.start(owner) // Status -> SYNCHRONIZING

    // Mock player queues initialization which happens in start()
    assertThat(game.playerActionQueues).isNotEmpty()

    // ready()
    game.ready(owner, 1)

    assertThat(game.status).isEqualTo(GameStatus.PLAYING)
  }

  @Test
  fun `quit removes player`() {
    whenever(owner.game).thenReturn(game)
    game.join(owner)

    game.quit(owner, 1)

    assertThat(game.players).doesNotContain(owner)
  }

  @Test
  fun `chat sends event`() {
    whenever(owner.game).thenReturn(game)
    game.join(owner)

    // Need to allow chat
    whenever(owner.accessLevel).thenReturn(AccessManager.ACCESS_NORMAL)
    whenever(server.maxGameChatLength).thenReturn(100)

    game.chat(owner, "Hello")

    verify(owner, atLeastOnce()).queueEvent(any()) // broadcast to players
  }

  companion object {
    private const val ROM_NAME = "Test Game"
    private const val GAME_ID = 123
  }
}
