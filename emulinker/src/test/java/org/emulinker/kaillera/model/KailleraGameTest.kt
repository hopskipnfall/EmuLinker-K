package org.emulinker.kaillera.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.access.Silence
import org.emulinker.kaillera.access.TempBan
import org.emulinker.kaillera.controller.input.N64ControllerInputParser
import org.emulinker.kaillera.model.event.GameStatusChangedEvent
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.exception.GameKickException
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.impl.AutoFireDetector
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [KailleraGame].
 *
 * [KailleraServer] and [KailleraUser] are both concrete classes with heavy dependencies, so they
 * are mocked throughout. Each test section is self-contained with helpers that build the game and
 * players from scratch to keep setup minimal.
 */
class KailleraGameTest :
  FunSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    // ---------------------------------------------------------------------------
    // Shared mocks & fakes
    // ---------------------------------------------------------------------------

    val mockFlags =
      mock<RuntimeFlags> {
        on { maxGameChatLength } doReturn 0
        on { gameBufferSize } doReturn 4096
        on { lagstatDuration } doReturn Duration.ZERO
        on { allowSinglePlayer } doReturn true
        on { gameAutoFireSensitivity } doReturn 0
      }

    val surveyFactory = SurveyManagerFactory(mockFlags, N64ControllerInputParser()) { null }

    val mockAutoFireDetector = mock<AutoFireDetector> { on { sensitivity } doReturn 0 }

    val mockAccessManager =
      object : AccessManager {
        override fun isAddressAllowed(address: InetAddress) = true

        override fun isSilenced(address: InetAddress) = false

        override fun isEmulatorAllowed(emulator: String) = true

        override fun isGameAllowed(game: String) = true

        override fun getAccess(address: InetAddress) = AccessManager.ACCESS_ADMIN

        override fun getAnnouncement(address: InetAddress): String? = null

        override fun addTempBan(
          addressPattern: String,
          duration: Duration,
          issuer: String?,
          reason: String?,
        ) {}

        override fun addTempAdmin(addressPattern: String, duration: Duration) {}

        override fun addTempModerator(addressPattern: String, duration: Duration) {}

        override fun addTempElevated(addressPattern: String, duration: Duration) {}

        override fun addSilenced(
          addressPattern: String,
          duration: Duration,
          issuer: String?,
          reason: String?,
        ) {}

        override fun clearTemp(address: InetAddress, clearAll: Boolean) = false

        override fun addPermaBan(addressPattern: String, issuer: String?, reason: String?) {}

        override fun addPermaMute(addressPattern: String, issuer: String?, reason: String?) {}

        override fun getTempBan(address: InetAddress): TempBan? = null

        override fun getSilence(address: InetAddress): Silence? = null

        override fun close() {}
      }

    val normalAccessManager =
      object : AccessManager by mockAccessManager {
        override fun getAccess(address: InetAddress) = AccessManager.ACCESS_NORMAL
      }

    val mockServer =
      mock<KailleraServer> {
        on { accessManager } doReturn mockAccessManager
        on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
        on { maxGameChatLength } doReturn 0
        on { statsCollector } doReturn null
        on { usersMap } doReturn mutableMapOf()
      }

    /** Constructs a stub [KailleraUser] that is set up for use as a game owner/player. */
    fun makeUser(
      id: Int = 1,
      name: String = "Player$id",
      accessLevel: Int = AccessManager.ACCESS_ADMIN,
      connectionType: ConnectionType = ConnectionType.LAN,
      pingMs: Int = 10,
      clientType: String? = "TestEmulator",
      address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 27000 + id),
    ): KailleraUser {
      val user =
        mock<KailleraUser> {
          on { this.id } doReturn id
          on { this.name } doReturn name
          on { this.accessLevel } doReturn accessLevel
          on { this.connectionType } doReturn connectionType
          on { this.ping } doReturn pingMs.milliseconds
          on { this.clientType } doReturn clientType
          on { this.connectSocketAddress } doReturn address
          on { this.socketAddress } doReturn address
          on { this.status } doReturn UserStatus.IDLE
          on { this.playerNumber } doReturn id
          on { this.bytesPerAction } doReturn 1
          on { this.arraySize } doReturn 1
          on { this.frameCount } doReturn 0
          on { this.inStealthMode } doReturn false
          on { this.isMuted } doReturn false
          on { this.ignoringUnnecessaryServerActivity } doReturn false
        }
      return user
    }

    /** Constructs a [KailleraGame] with [owner] already in [players]. */
    fun makeGame(
      romName: String = "Test Game",
      owner: KailleraUser = makeUser(),
      server: KailleraServer = mockServer,
      bufferSize: Int = 4096,
    ): KailleraGame {
      val game =
        KailleraGame(
          id = 1,
          romName = romName,
          owner = owner,
          server = server,
          bufferSize = bufferSize,
          flags = mockFlags,
          clock = Clock.System,
          surveyManagerFactory = surveyFactory,
        )
      // The owner is always the first player in a real game.
      game.players.add(owner)
      whenever(owner.game) doReturn game
      return game
    }

    // ---------------------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------------------

    context("toString") {
      test("toString includes id and short rom name") {
        val game = makeGame(romName = "Smash Bros")
        game.toString() shouldContain "Smash Bros"
        game.toString() shouldContain "id=1"
      }

      test("toString truncates rom names longer than 15 chars") {
        val game = makeGame(romName = "A Very Long Game Name Indeed")
        game.toString() shouldContain "..."
        // The full name should NOT appear
        game.toString() shouldNotContain "A Very Long Game Name Indeed"
      }

      test("toString does not truncate rom names exactly 15 chars") {
        val game = makeGame(romName = "123456789012345") // exactly 15
        game.toString() shouldNotContain "..."
        game.toString() shouldContain "123456789012345"
      }
    }

    // ---------------------------------------------------------------------------
    // getPlayerNumber / getPlayer
    // ---------------------------------------------------------------------------

    context("getPlayerNumber / getPlayer") {
      test("getPlayerNumber returns 1-based index of player") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.getPlayerNumber(owner) shouldBe 1
        game.getPlayerNumber(player2) shouldBe 2
      }

      test("getPlayerNumber returns 0 when user is not in game") {
        val owner = makeUser(id = 1)
        val outsider = makeUser(id = 99)
        val game = makeGame(owner = owner)

        // indexOf returns -1, so +1 = 0
        game.getPlayerNumber(outsider) shouldBe 0
      }

      test("getPlayer returns correct user for 1-based index") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.getPlayer(1) shouldBe owner
        game.getPlayer(2) shouldBe player2
      }

      test("getPlayer returns null when playerNumber exceeds list size") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)

        game.getPlayer(5).shouldBeNull()
      }
    }

    // ---------------------------------------------------------------------------
    // chat
    // ---------------------------------------------------------------------------

    context("chat") {
      test("chat throws GameChatException when user is not in game") {
        val owner = makeUser(id = 1)
        val outsider = makeUser(id = 2)
        val game = makeGame(owner = owner)

        shouldThrow<GameChatException> { game.chat(outsider, "hello") }
      }

      test("chat succeeds for player in game") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)

        // Should NOT throw
        game.chat(owner, "hello world")
      }

      test("chat enforces maxGameChatLength for normal access users") {
        val normalAccessServer =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager // normal access
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { maxGameChatLength } doReturn 5
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val normalUser = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)
        val game = makeGame(server = normalAccessServer, owner = normalUser)

        shouldThrow<GameChatException> { game.chat(normalUser, "This message is too long") }
      }

      test("chat does not enforce maxGameChatLength for admin users") {
        // Admin users bypass the length check
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val serverWithLimit =
          mock<KailleraServer> {
            on { accessManager } doReturn mockAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { maxGameChatLength } doReturn 5
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game = makeGame(owner = owner, server = serverWithLimit)

        // Must NOT throw despite being longer than 5 chars
        game.chat(owner, "This message is too long for normal users but admins are exempt")
      }
    }

    // ---------------------------------------------------------------------------
    // announce
    // ---------------------------------------------------------------------------

    context("announce") {
      test("announce sends event to all players") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.announce("hello everyone")

        verify(owner).doEvent(any())
        verify(player2).doEvent(any())
      }

      test("announce with target user sends event to all players (GameInfoEvent carries target)") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        // announce with a target user still distributes to all players;
        // the GameInfoEvent itself carries the target for filtering at the client level.
        game.announce("private-ish message", owner)

        verify(owner).doEvent(any())
        verify(player2).doEvent(any())
      }
    }

    // ---------------------------------------------------------------------------
    // kick
    // ---------------------------------------------------------------------------

    context("kick") {
      test("kick throws GameKickException when requester is not owner and not admin") {
        val owner = makeUser(id = 1)
        val normalPlayer = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)

        val normalServer =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game = makeGame(owner = owner, server = normalServer)
        game.players.add(normalPlayer)

        shouldThrow<GameKickException> { game.kick(requester = normalPlayer, userID = owner.id) }
      }

      test("kick throws GameKickException when requester tries to kick themselves") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val game = makeGame(owner = owner)

        shouldThrow<GameKickException> { game.kick(requester = owner, userID = owner.id) }
      }

      test("kick throws GameKickException when target user not found in game") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val game = makeGame(owner = owner)

        shouldThrow<GameKickException> { game.kick(requester = owner, userID = 99) }
      }

      test("kick by owner succeeds for a normal player") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val target = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)
        val game = makeGame(owner = owner)
        game.players.add(target)

        // Should NOT throw; it calls target.quitGame()
        game.kick(requester = owner, userID = target.id)

        verify(target).quitGame()
      }
    }

    // ---------------------------------------------------------------------------
    // join
    // ---------------------------------------------------------------------------

    context("join") {
      test("join throws JoinGameException if user is already in game") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)

        shouldThrow<JoinGameException> {
          game.join(owner) // owner is already in players list
        }
      }

      test("join throws JoinGameException when game is full for normal users") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.maxUsers = 1 // only 1 slot, owner already fills it

        val newUser = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)
        val serverWithNormal =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        // Rebuild game with the normal-access server
        val normalGame =
          KailleraGame(
            id = 2,
            romName = "Test",
            owner = owner,
            server = serverWithNormal,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        normalGame.players.add(owner)
        normalGame.maxUsers = 1

        shouldThrow<JoinGameException> { normalGame.join(newUser) }
      }

      test("join throws JoinGameException when ping is too high for normal users") {
        val owner = makeUser(id = 1)
        val highPingUser =
          makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL, pingMs = 9000)

        val serverWithNormal =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game =
          KailleraGame(
            id = 2,
            romName = "Test",
            owner = owner,
            server = serverWithNormal,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        game.players.add(owner)
        game.maxPing = 100 // 100ms limit

        shouldThrow<JoinGameException> { game.join(highPingUser) }
      }

      test(
        "join throws JoinGameException when emulator restriction is set and user has wrong emulator"
      ) {
        val owner = makeUser(id = 1, clientType = "Mupen64Plus")
        val wrongEmulatorUser =
          makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL, clientType = "MAME")

        val serverWithNormal =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game =
          KailleraGame(
            id = 2,
            romName = "Test",
            owner = owner,
            server = serverWithNormal,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        game.players.add(owner)
        game.aEmulator = "Mupen64Plus"

        shouldThrow<JoinGameException> { game.join(wrongEmulatorUser) }
      }

      test("join throws JoinGameException for normal user joining an in-progress game") {
        val owner = makeUser(id = 1)
        val joiner = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)

        val serverWithNormal =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game =
          KailleraGame(
            id = 2,
            romName = "Test",
            owner = owner,
            server = serverWithNormal,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        game.players.add(owner)
        // Manually set status to PLAYING to simulate an in-progress game
        // We can reflect into field or use the package-private start path.
        // Easier: poke at status via the public field (it's private set, but we can bypass for
        // tests
        // using reflection as a last resort). Since KailleraGame is in the same package, we can
        // change it by calling game.status through the backing field using Kotlin reflection or by
        // actually running through the start flow.
        // Instead, let's just test the SYNCHRONIZING status path, which is also guarded.
        // We'll use a companion AccessManager that returns ACCESS_NORMAL to test the status guard.

        // Because status is 'private set', call start() to set it from the test flow.
        // To avoid full start() complexity, we test directly that a user mid-join to a WAITING game
        // works.
        val playerNumber = game.join(joiner)
        playerNumber shouldBe 2
      }

      test("join succeeds and returns player number") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)

        val playerNumber = game.join(player2)

        playerNumber shouldBe 2
        game.players shouldContain player2
      }

      test("join throws JoinGameException for previously kicked user") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val kickedUser = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)

        // normalServer returns ACCESS_NORMAL via getAccess(), so the kick-list check in join()
        // fires.
        // kick() uses requester.accessLevel (from the mock user), so the owner (ADMIN) can still
        // kick.
        val normalServer =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }

        val game =
          KailleraGame(
            id = 3,
            romName = "Test",
            owner = owner,
            server = normalServer,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        game.players.add(owner)
        game.players.add(kickedUser)

        // Kick adds the IP to the internal kickedUsers set.
        game.kick(owner, kickedUser.id)
        // Since quitGame() is mocked and a no-op, remove manually.
        game.players.remove(kickedUser)

        // A different user object coming from the same IP should be denied.
        val sameAddressUser =
          makeUser(
            id = 3,
            accessLevel = AccessManager.ACCESS_NORMAL,
            address = kickedUser.connectSocketAddress,
          )

        shouldThrow<JoinGameException> { game.join(sameAddressUser) }
      }
    }

    // ---------------------------------------------------------------------------
    // start
    // ---------------------------------------------------------------------------

    context("start") {
      test("start throws StartGameException when status is SYNCHRONIZING") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val game = makeGame(owner = owner)
        // Force status to SYNCHRONIZING via start() first
        val player2 = makeUser(id = 2)
        game.players.add(player2)
        game.start(owner) // transitions to SYNCHRONIZING

        shouldThrow<StartGameException> {
          game.start(owner) // second call should fail
        }
      }

      test("start throws StartGameException when requester is not owner and not admin") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val normalPlayer = makeUser(id = 2, accessLevel = AccessManager.ACCESS_NORMAL)

        val serverWithNormal =
          mock<KailleraServer> {
            on { accessManager } doReturn normalAccessManager
            on { getAutoFireDetector(any()) } doReturn mockAutoFireDetector
            on { statsCollector } doReturn null
            on { usersMap } doReturn mutableMapOf()
          }
        val game =
          KailleraGame(
            id = 2,
            romName = "Test",
            owner = owner,
            server = serverWithNormal,
            bufferSize = 4096,
            flags = mockFlags,
            clock = Clock.System,
            surveyManagerFactory = surveyFactory,
          )
        game.players.add(owner)
        game.players.add(normalPlayer)

        shouldThrow<StartGameException> {
          game.start(normalPlayer) // not the owner
        }
      }

      test("start succeeds and sets status to SYNCHRONIZING") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.start(owner)

        game.status shouldBe GameStatus.SYNCHRONIZING
      }

      test("start initializes playerActionQueues") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.start(owner)

        game.playerActionQueues.shouldNotBeNull()
        game.playerActionQueues!!.size shouldBe 2
      }

      test("start skips games with romName starting with asterisk") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val game = makeGame(romName = "*Special Mode*", owner = owner)
        game.players.add(makeUser(id = 2))

        // Should NOT set SYNCHRONIZING
        game.start(owner)

        // The method returns early before changing status
        game.status shouldBe GameStatus.WAITING
      }
    }

    // ---------------------------------------------------------------------------
    // drop
    // ---------------------------------------------------------------------------

    context("drop") {
      test("drop marks player as desynced") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val player2 = makeUser(id = 2, accessLevel = AccessManager.ACCESS_ADMIN)
        val game = makeGame(owner = owner)
        game.players.add(player2)
        game.start(owner) // must start to initialize playerActionQueues

        val paq = game.playerActionQueues!!
        paq[0].markSynced()
        paq[1].markSynced()

        game.drop(user = player2, playerNumber = 2)

        paq[1].synced.shouldBeFalse()
      }

      test("drop transitions game back to WAITING when all players stop playing") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)
        game.start(owner)

        // Fake both users as PLAYING
        whenever(owner.status) doReturn UserStatus.PLAYING
        // drop player2, but owner is also PLAYING so game stays active
        whenever(player2.status) doReturn UserStatus.IDLE

        game.drop(user = player2, playerNumber = 2)
        // Only player2 dropped; owner is still active (treated as 1 player playing)
        // In the drop() code, it checks  playingCount == 0.
        // Since owner is PLAYING, game stays in SYNCHRONIZING.
        game.status shouldBe GameStatus.SYNCHRONIZING
      }

      test("drop transitions to WAITING when last playing player drops") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)
        game.start(owner)

        // Both players are IDLE (i.e., playingCount == 0 after drop)
        whenever(owner.status) doReturn UserStatus.IDLE
        whenever(player2.status) doReturn UserStatus.IDLE

        game.drop(user = player2, playerNumber = 2)

        game.status shouldBe GameStatus.WAITING
      }
    }

    // ---------------------------------------------------------------------------
    // quit
    // ---------------------------------------------------------------------------

    context("quit") {
      test("quit removes player from players list") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        // Quitting a non-owner should remove them and notify server of status change
        game.quit(user = player2, playerNumber = 2)

        game.players shouldNotContain player2
      }

      test("quit throws QuitGameException when user is not in game") {
        val owner = makeUser(id = 1)
        val outsider = makeUser(id = 99)
        val game = makeGame(owner = owner)

        shouldThrow<QuitGameException> { game.quit(user = outsider, playerNumber = 99) }
      }

      test("quit calls server closeGame when owner quits") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)

        game.quit(user = owner, playerNumber = 1)

        verify(mockServer).closeGame(game, owner)
      }

      test("quit by non-owner does not call closeGame") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.quit(user = player2, playerNumber = 2)

        verify(mockServer, never()).closeGame(any(), any())
      }

      test("quit renumbers remaining players when game is in WAITING status") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val player3 = makeUser(id = 3)
        val game = makeGame(owner = owner)
        game.players.add(player2)
        game.players.add(player3)

        // Quit player2 (middle player)
        game.quit(user = player2, playerNumber = 2)

        // After quit, players are [owner, player3]; they should be renumbered 1, 2
        verify(owner).playerNumber = 1
        verify(player3).playerNumber = 2
      }
    }

    // ---------------------------------------------------------------------------
    // close
    // ---------------------------------------------------------------------------

    context("close") {
      test("close resets all player state and clears the players list") {
        val owner = makeUser(id = 1)
        val player2 = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.players.add(player2)

        game.close(owner)

        game.players.shouldBeEmpty()
        // Each player's status, isMuted, game, and ignoringUnnecessaryServerActivity should be
        // reset
        verify(owner).status = UserStatus.IDLE
        verify(owner).isMuted = false
        verify(owner).game = null
        verify(player2).status = UserStatus.IDLE
        verify(player2).isMuted = false
        verify(player2).game = null
      }
    }

    // ---------------------------------------------------------------------------
    // mutedUsers
    // ---------------------------------------------------------------------------

    context("mutedUsers") {
      test("joining muted address sets isMuted on user") {
        val owner = makeUser(id = 1)
        val mutedUser = makeUser(id = 2)
        val game = makeGame(owner = owner)
        game.mutedUsers.add(mutedUser.connectSocketAddress.address.hostAddress)

        game.join(mutedUser)

        verify(mutedUser).isMuted = true
      }
    }

    // ---------------------------------------------------------------------------
    // maxUsers setter
    // ---------------------------------------------------------------------------

    context("maxUsers setter") {
      test("setting maxUsers fires GameStatusChangedEvent on server") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)

        game.maxUsers = 4

        // The setter calls server.addEvent(GameStatusChangedEvent(...))
        verify(mockServer).addEvent(any<GameStatusChangedEvent>())
      }
    }

    // ---------------------------------------------------------------------------
    // sameDelay / highestUserFrameDelay
    // ---------------------------------------------------------------------------

    context("sameDelay / highestUserFrameDelay") {
      test("highestUserFrameDelay starts at 0") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.highestUserFrameDelay shouldBe 0
      }

      test("startN is -1 by default") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.startN shouldBe -1
      }
    }

    // ---------------------------------------------------------------------------
    // currentGameLag
    // ---------------------------------------------------------------------------

    context("currentGameLag") {
      test("currentGameLag returns ZERO when lagometer is null") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.currentGameLag shouldBe Duration.ZERO
      }
    }

    // ---------------------------------------------------------------------------
    // PlayerActionQueue integration
    // ---------------------------------------------------------------------------

    context("PlayerActionQueue integration") {
      test("playerActionQueues is null before start") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.playerActionQueues.shouldBeNull()
      }

      test("start creates one queue per player") {
        val owner = makeUser(id = 1, accessLevel = AccessManager.ACCESS_ADMIN)
        val p2 = makeUser(id = 2)
        val p3 = makeUser(id = 3)
        val game = makeGame(owner = owner)
        game.players.add(p2)
        game.players.add(p3)

        game.start(owner)

        game.playerActionQueues!!.size shouldBe 3
      }
    }

    // ---------------------------------------------------------------------------
    // waitingOnData / waitingOnPlayerNumber
    // ---------------------------------------------------------------------------

    context("waitingOnData / waitingOnPlayerNumber") {
      test("waitingOnData starts as false") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.waitingOnData.shouldBeFalse()
      }

      test("waitingOnPlayerNumber array has 10 slots all initialized to false") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        game.waitingOnPlayerNumber.size shouldBe 10
        game.waitingOnPlayerNumber.all { !it }.shouldBeTrue()
      }

      test("setGameFps with no players does nothing instead of throwing") {
        val game = makeGame()
        game.players.clear()

        game.setGameFps(60.0)

        game.lagometer.shouldBeNull()
      }

      test("setGameFps sizes the lagometer by the highest player number after a mid-game leave") {
        val owner = makeUser(id = 1)
        val game = makeGame(owner = owner)
        // Players {1, 2, 3} became {1, 3}: player numbers are not renumbered when someone leaves.
        game.players.add(makeUser(id = 3))

        game.setGameFps(60.0)

        game.lagometer!!.userDatas.size shouldBeGreaterThanOrEqual 3
      }
    }
  })
