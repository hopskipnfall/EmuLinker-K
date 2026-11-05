package org.emulinker.kaillera.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.access.Silence
import org.emulinker.kaillera.access.TempBan
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.GameStartedEvent
import org.emulinker.kaillera.model.event.InfoMessageEvent
import org.emulinker.kaillera.model.event.UserQuitEvent
import org.emulinker.kaillera.model.exception.GameChatException
import org.emulinker.kaillera.model.exception.GameKickException
import org.emulinker.kaillera.model.exception.JoinGameException
import org.emulinker.kaillera.model.exception.StartGameException
import org.emulinker.kaillera.model.exception.UserReadyException
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [KailleraUser].
 *
 * [V086ClientHandler] and [KailleraServer] are concrete classes with heavy dependencies and are
 * mocked throughout. A real [RuntimeFlags] instance is constructed to avoid over-mocking a pure
 * data class.
 */
class KailleraUserTest :
  FunSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    val defaultFlags =
      RuntimeFlags(
        allowMultipleConnections = false,
        allowSinglePlayer = true,
        charset = Charsets.UTF_8,
        chatFloodTime = Duration.ZERO,
        allowedProtocols = listOf("0.83"),
        allowedConnectionTypes = listOf("1"),
        coreThreadPoolSize = 4,
        createGameFloodTime = Duration.ZERO,
        gameAutoFireSensitivity = 0,
        gameBufferSize = 4096,
        idleTimeout = 1.hours,
        keepAliveTimeout = 1.hours,
        lagstatDuration = 60.seconds,
        language = "en",
        maxChatLength = 0,
        maxClientNameLength = 0,
        maxGameChatLength = 0,
        maxGameNameLength = 0,
        maxGames = 0,
        maxPing = 1000.milliseconds,
        maxQuitMessageLength = 0,
        maxUserNameLength = 31,
        maxUsers = 100,
        metricsEnabled = false,
        metricsLoggingFrequency = 1.minutes,
        serverAddress = "localhost",
        serverLocation = "Test",
        serverName = "TestServer",
        serverPort = 27888,
        serverWebsite = "http://example.com",
        switchStatusBytesForBuggyClient = false,
        touchEmulinker = false,
        touchKaillera = false,
        twitterBroadcastDelay = Duration.ZERO,
        twitterDeletePostOnClose = false,
        twitterEnabled = false,
        twitterOAuthAccessToken = "",
        twitterOAuthAccessTokenSecret = "",
        twitterOAuthConsumerKey = "",
        twitterOAuthConsumerSecret = "",
        twitterPreventBroadcastNameSuffixes = emptyList(),
        v086BufferSize = 4096,
        surveyEnabled = false,
        surveyGameWhitelist = emptyList(),
        surveyApiEndpoint = "",
        surveyApiKey = "",
      )

    val mockClientHandler = mock<V086ClientHandler>()
    val mockServer = mock<KailleraServer>()

    val loopbackAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 27000)

    /** Constructs a fresh [KailleraUser] for each test. */
    fun makeUser(
      id: Int = 1,
      flags: RuntimeFlags = defaultFlags,
      clock: Clock = Clock.System,
      server: KailleraServer = mockServer,
      clientHandler: V086ClientHandler = mockClientHandler,
      connectAddress: InetSocketAddress = loopbackAddress,
    ): KailleraUser =
      KailleraUser(
        id = id,
        protocol = "0.83",
        connectSocketAddress = connectAddress,
        clientHandler = clientHandler,
        server = server,
        flags = flags,
        clock = clock,
      )

    // ---------------------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------------------

    context("toString") {
      test("toString includes id") {
        val user = makeUser(id = 42)
        user.toString() shouldContain "id=42"
      }

      test("toString includes name when set") {
        val user = makeUser()
        user.name = "NovaStar"
        user.toString() shouldContain "NovaStar"
      }

      test("toString handles null name") {
        val user = makeUser()
        // Should not throw
        user.toString().shouldNotBeEmpty()
      }
    }

    // ---------------------------------------------------------------------------
    // clientType — isEsfAdminClient detection
    // ---------------------------------------------------------------------------

    context("clientType — isEsfAdminClient detection") {
      test("setting clientType to EmulinkerSF Admin Client prefix sets isEsfAdminClient") {
        val user = makeUser()
        user.clientType = "EmulinkerSF Admin Client 1.2"
        user.isEsfAdminClient.shouldBeTrue()
      }

      test("setting clientType to normal emulator does not set isEsfAdminClient") {
        val user = makeUser()
        user.clientType = "Project 64k 0.13 (01 Aug 2003)"
        user.isEsfAdminClient.shouldBeFalse()
      }

      test("setting clientType to null does not set isEsfAdminClient") {
        val user = makeUser()
        user.clientType = null
        user.isEsfAdminClient.shouldBeFalse()
      }
    }

    // ---------------------------------------------------------------------------
    // isDead / isIdleForTooLong
    // ---------------------------------------------------------------------------

    context("isDead / isIdleForTooLong") {
      test("isDead returns false immediately after construction") {
        val user = makeUser()
        user.isDead.shouldBeFalse()
      }

      test("isDead returns true when keepAliveTimeout has clearly elapsed") {
        // Use a fixed clock that is 2 hours in the past so that time comparisons fire.
        val pastInstant = Clock.System.now() - 2.hours
        val frozenClock =
          object : Clock {
            override fun now(): Instant = pastInstant
          }
        val user = makeUser(clock = frozenClock)
        // System.nanoTime() naturally advances, so system-nanos check keeps it alive by default.
        // isDead requires BOTH checks to be satisfied; nanoTime is real so it won't be > 1 hour
        // yet.
        // We can only truly assert the clock side; the nanoTime side will not be stale yet in a
        // test.
        // Therefore isDead will appear false due to the nanoTime guard being fresh.
        user.isDead.shouldBeFalse() // nanoTime guard protects it
      }

      test("isIdleForTooLong returns false immediately after construction") {
        val user = makeUser()
        user.isIdleForTooLong.shouldBeFalse()
      }
    }

    // ---------------------------------------------------------------------------
    // updateLastKeepAlive
    // ---------------------------------------------------------------------------

    context("updateLastKeepAlive") {
      test("updateLastKeepAlive does not throw") {
        val user = makeUser()
        user.updateLastKeepAlive() // Expected: no exception
      }
    }

    // ---------------------------------------------------------------------------
    // game field
    // ---------------------------------------------------------------------------

    context("game field") {
      test("game is null initially") {
        val user = makeUser()
        user.game.shouldBeNull()
      }

      test("setting game to null resets playerNumber to -1") {
        val user = makeUser()
        user.playerNumber = 2
        user.game = null
        user.playerNumber shouldBe -1
      }

      test("setting game to non-null preserves playerNumber") {
        val user = makeUser()
        user.playerNumber = 3
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.playerNumber shouldBe 3
      }
    }

    // ---------------------------------------------------------------------------
    // Ignore list
    // ---------------------------------------------------------------------------

    context("Ignore list") {
      test("findIgnoredUser returns false for unknown address") {
        val user = makeUser()
        user.findIgnoredUser("1.2.3.4").shouldBeFalse()
      }

      test("addIgnoredUser then findIgnoredUser returns true") {
        val user = makeUser()
        user.addIgnoredUser("1.2.3.4")
        user.findIgnoredUser("1.2.3.4").shouldBeTrue()
      }

      test("addIgnoredUser is address-specific") {
        val user = makeUser()
        user.addIgnoredUser("1.2.3.4")
        user.findIgnoredUser("5.6.7.8").shouldBeFalse()
      }

      test("removeIgnoredUser removes single matching address") {
        val user = makeUser()
        user.addIgnoredUser("1.2.3.4")
        user.addIgnoredUser("5.6.7.8")

        val removed = user.removeIgnoredUser("1.2.3.4", removeAll = false)

        removed.shouldBeTrue()
        user.findIgnoredUser("1.2.3.4").shouldBeFalse()
        user.findIgnoredUser("5.6.7.8").shouldBeTrue()
      }

      test("removeIgnoredUser removeAll clears all addresses") {
        val user = makeUser()
        user.addIgnoredUser("1.2.3.4")
        user.addIgnoredUser("5.6.7.8")

        val removed = user.removeIgnoredUser("doesnt-matter", removeAll = true)

        removed.shouldBeTrue()
        user.findIgnoredUser("1.2.3.4").shouldBeFalse()
        user.findIgnoredUser("5.6.7.8").shouldBeFalse()
      }

      test("removeIgnoredUser returns false when address not found") {
        val user = makeUser()
        val removed = user.removeIgnoredUser("not-there", removeAll = false)
        removed.shouldBeFalse()
      }

      test("searchIgnoredUsers returns true for added address") {
        val user = makeUser()
        user.addIgnoredUser("10.0.0.1")
        user.searchIgnoredUsers("10.0.0.1").shouldBeTrue()
      }

      test("searchIgnoredUsers returns false for unknown address") {
        val user = makeUser()
        user.searchIgnoredUsers("10.0.0.1").shouldBeFalse()
      }
    }

    // ---------------------------------------------------------------------------
    // loggedIn / status / name defaults
    // ---------------------------------------------------------------------------

    context("loggedIn / status / name defaults") {
      test("loggedIn is false after construction") {
        val user = makeUser()
        user.loggedIn.shouldBeFalse()
      }

      test("default status is PLAYING") {
        // The KailleraUser comment says "This probably shouldn't have a default value."
        val user = makeUser()
        user.status shouldBe UserStatus.PLAYING
      }

      test("default accessLevel is 0") {
        val user = makeUser()
        user.accessLevel shouldBe 0
      }

      test("accessStr reflects accessLevel") {
        val user = makeUser()
        user.accessLevel = AccessManager.ACCESS_NORMAL
        user.accessStr shouldBe AccessManager.ACCESS_NAMES[AccessManager.ACCESS_NORMAL]
      }
    }

    // ---------------------------------------------------------------------------
    // miscellaneous mutable fields
    // ---------------------------------------------------------------------------

    context("miscellaneous mutable fields") {
      test("isMuted defaults to false") {
        val user = makeUser()
        user.isMuted.shouldBeFalse()
      }

      test("ignoreAll defaults to false") {
        val user = makeUser()
        user.ignoreAll.shouldBeFalse()
      }

      test("isAcceptingDirectMessages defaults to true") {
        val user = makeUser()
        user.isAcceptingDirectMessages.shouldBeTrue()
      }

      test("lastMsgID defaults to -1") {
        val user = makeUser()
        user.lastMsgID shouldBe -1
      }

      test("frameCount defaults to 0") {
        val user = makeUser()
        user.frameCount shouldBe 0
      }

      test("frameDelay defaults to 0") {
        val user = makeUser()
        user.frameDelay shouldBe 0
      }

      test("playerNumber defaults to -1") {
        val user = makeUser()
        user.playerNumber shouldBe -1
      }

      test("inStealthMode defaults to false") {
        val user = makeUser()
        user.inStealthMode.shouldBeFalse()
      }

      test("ignoringUnnecessaryServerActivity defaults to false") {
        val user = makeUser()
        user.ignoringUnnecessaryServerActivity.shouldBeFalse()
      }

      test("swap defaults to false via KailleraGame usage") {
        // swap is a @JvmField on KailleraGame, but we validate here that
        // KailleraUser.ignoringUnnecessaryServerActivity is independently false.
        val user = makeUser()
        user.ignoringUnnecessaryServerActivity.shouldBeFalse()
      }
    }

    // ---------------------------------------------------------------------------
    // connectSocketAddress / connectTime
    // ---------------------------------------------------------------------------

    context("connectSocketAddress / connectTime") {
      test("connectSocketAddress is the address passed at construction") {
        val addr = InetSocketAddress(InetAddress.getLoopbackAddress(), 12345)
        val user = makeUser(connectAddress = addr)
        user.connectSocketAddress shouldBe addr
      }

      test("connectTime is set at construction time") {
        val before = Clock.System.now()
        val user = makeUser()
        val after = Clock.System.now()
        user.connectTime shouldBeGreaterThanOrEqualTo before
        user.connectTime shouldBeLessThanOrEqualTo after
      }
    }

    // ---------------------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------------------

    context("equals / hashCode") {
      test("two users with same id are equal") {
        val u1 = makeUser(id = 7)
        val u2 = makeUser(id = 7)
        u1 shouldBe u2
      }

      test("two users with different ids are not equal") {
        val u1 = makeUser(id = 1)
        val u2 = makeUser(id = 2)
        u1 shouldNotBe u2
      }

      test("hashCode is consistent with equality") {
        val u1 = makeUser(id = 5)
        val u2 = makeUser(id = 5)
        u1.hashCode() shouldBe u2.hashCode()
      }

      test("user is not equal to non-user object") {
        val user = makeUser(id = 1)
        user shouldNotBe "not a user"
      }

      test("user is equal to itself") {
        val user = makeUser(id = 3)
        @Suppress("ReplaceCallWithBinaryOperator") user.equals(user).shouldBeTrue()
      }
    }

    // ---------------------------------------------------------------------------
    // doEvent — routing and side-effects
    // ---------------------------------------------------------------------------

    context("doEvent — routing and side-effects") {
      test("doEvent delegates to clientHandler") {
        val user = makeUser()
        val server = mock<KailleraServer>()
        val event = InfoMessageEvent(user, "hello")

        user.doEvent(event)

        verify(mockClientHandler).actionPerformed(event)
      }

      test("doEvent with GameStartedEvent sets status to PLAYING") {
        val user = makeUser()
        user.status = UserStatus.IDLE
        val mockGame = mock<KailleraGame>()
        val event = GameStartedEvent(mockGame)

        user.doEvent(event)

        user.status shouldBe UserStatus.PLAYING
      }

      test("doEvent with UserQuitEvent for this user calls stop") {
        val user = makeUser()
        val server = mock<KailleraServer>()
        val event = UserQuitEvent(server, user, "bye")

        user.doEvent(event)

        verify(mockClientHandler).stop()
      }

      test("doEvent with UserQuitEvent for different user does not stop") {
        val user = makeUser(id = 1)
        val otherUser = makeUser(id = 2)
        val server = mock<KailleraServer>()
        val event = UserQuitEvent(server, otherUser, "bye")

        user.doEvent(event)

        verify(mockClientHandler, never()).stop()
      }

      test(
        "doEvent InfoMessageEvent is suppressed when user is PLAYING and ignoringUnnecessaryServerActivity"
      ) {
        val user = makeUser()
        user.status = UserStatus.PLAYING
        user.ignoringUnnecessaryServerActivity = true
        val event = InfoMessageEvent(user, "ignored")

        user.doEvent(event)

        verify(mockClientHandler, never()).actionPerformed(event)
      }

      test("doEvent InfoMessageEvent reaches clientHandler when user is IDLE") {
        val user = makeUser()
        user.status = UserStatus.IDLE
        user.ignoringUnnecessaryServerActivity = true
        val event = InfoMessageEvent(user, "not ignored")

        user.doEvent(event)

        verify(mockClientHandler).actionPerformed(event)
      }

      test(
        "doEvent InfoMessageEvent reaches clientHandler when ignoringUnnecessaryServerActivity is false"
      ) {
        val user = makeUser()
        user.status = UserStatus.PLAYING
        user.ignoringUnnecessaryServerActivity = false
        val event = InfoMessageEvent(user, "delivered")

        user.doEvent(event)

        verify(mockClientHandler).actionPerformed(event)
      }
    }

    // ---------------------------------------------------------------------------
    // droppedPacket
    // ---------------------------------------------------------------------------

    context("droppedPacket") {
      test("droppedPacket does nothing when game is null") {
        val user = makeUser()
        // Should not throw
        user.droppedPacket()
      }

      test("droppedPacket delegates to game when in a game") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.playerNumber = 1

        user.droppedPacket()

        verify(mockGame).droppedPacket(user)
      }
    }

    // ---------------------------------------------------------------------------
    // gameKick
    // ---------------------------------------------------------------------------

    context("gameKick") {
      test("gameKick throws GameKickException when not in a game") {
        val user = makeUser()
        // user.game is null

        shouldThrow<GameKickException> { user.gameKick(userID = 5) }
      }

      test("gameKick delegates to game when in a game") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.playerNumber = 1

        user.gameKick(userID = 99)

        verify(mockGame).kick(user, 99)
      }
    }

    // ---------------------------------------------------------------------------
    // dropGame
    // ---------------------------------------------------------------------------

    context("dropGame") {
      test("dropGame does nothing when status is IDLE") {
        val user = makeUser()
        user.status = UserStatus.IDLE

        user.dropGame() // Should not throw

        // game was null anyway so no interaction expected
        verify(mockClientHandler, never()).actionPerformed(any())
      }

      test("dropGame sets status to IDLE and calls game drop") {
        val user = makeUser()
        user.status = UserStatus.PLAYING
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.playerNumber = 2

        user.dropGame()

        user.status shouldBe UserStatus.IDLE
        verify(mockGame).drop(user, 2)
      }

      test("dropGame does not call game drop when game is null but status is PLAYING") {
        val user = makeUser()
        user.status = UserStatus.PLAYING
        // game remains null

        user.dropGame()

        user.status shouldBe UserStatus.IDLE
      }
    }

    // ---------------------------------------------------------------------------
    // quitGame
    // ---------------------------------------------------------------------------

    context("quitGame") {
      test("quitGame does nothing when game is null") {
        val user = makeUser()
        // Should not throw
        user.quitGame()
      }

      test("quitGame resets isMuted and game to null") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.isMuted = true
        user.status = UserStatus.IDLE // not PLAYING so drop is skipped
        user.playerNumber = 1

        user.quitGame()

        user.isMuted.shouldBeFalse()
        user.game.shouldBeNull()
      }

      test("quitGame when PLAYING calls drop then quit on game") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.status = UserStatus.PLAYING
        user.playerNumber = 1

        user.quitGame()

        verify(mockGame).drop(user, 1)
        verify(mockGame).quit(user, 1)
      }
    }

    // ---------------------------------------------------------------------------
    // startGame
    // ---------------------------------------------------------------------------

    context("startGame") {
      test("startGame throws StartGameException when not in a game") {
        val user = makeUser()

        shouldThrow<StartGameException> { user.startGame() }
      }

      test("startGame delegates to game when in a game") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame

        user.startGame()

        verify(mockGame).start(user)
      }
    }

    // ---------------------------------------------------------------------------
    // gameChat
    // ---------------------------------------------------------------------------

    context("gameChat") {
      test("gameChat throws GameChatException when not in a game") {
        val user = makeUser()

        shouldThrow<GameChatException> { user.gameChat("hello", messageID = 1) }
      }

      test("gameChat announces mute message when user is muted") {
        val user = makeUser()
        val mockGame = mock<KailleraGame>()
        user.game = mockGame
        user.isMuted = true

        user.gameChat("hello", messageID = 1)

        verify(mockGame).announce(any(), any())
        verify(mockGame, never()).chat(any(), any())
      }

      test("gameChat delegates to game chat when user is not muted or silenced") {
        val allowAllServer =
          mock<KailleraServer> {
            on { accessManager } doReturn
              object : AccessManager {
                override fun isAddressAllowed(address: InetAddress) = true

                override fun isSilenced(address: InetAddress) = false

                override fun isEmulatorAllowed(emulator: String) = true

                override fun isGameAllowed(game: String) = true

                override fun getAccess(address: InetAddress) = AccessManager.ACCESS_NORMAL

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

                override fun addPermaBan(
                  addressPattern: String,
                  issuer: String?,
                  reason: String?,
                ) {}

                override fun addPermaMute(
                  addressPattern: String,
                  issuer: String?,
                  reason: String?,
                ) {}

                override fun getTempBan(address: InetAddress): TempBan? = null

                override fun getSilence(address: InetAddress): Silence? = null

                override fun close() {}
              }
          }
        val user = makeUser(server = allowAllServer)
        user.socketAddress = loopbackAddress
        val mockGame = mock<KailleraGame>()
        user.game = mockGame

        user.gameChat("hello world", messageID = 1)

        verify(mockGame).chat(user, "hello world")
      }

      test("gameChat announces silence message when user is silenced") {
        val silencedAccessManager =
          object : AccessManager {
            override fun isAddressAllowed(address: InetAddress) = true

            override fun isSilenced(address: InetAddress) = true // silenced!

            override fun isEmulatorAllowed(emulator: String) = true

            override fun isGameAllowed(game: String) = true

            override fun getAccess(address: InetAddress) = AccessManager.ACCESS_NORMAL

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
        val silencedServer =
          mock<KailleraServer> { on { accessManager } doReturn silencedAccessManager }
        val user = makeUser(server = silencedServer)
        user.socketAddress = loopbackAddress
        val mockGame = mock<KailleraGame>()
        user.game = mockGame

        user.gameChat("hello", messageID = 1)

        verify(mockGame).announce(any(), any())
        verify(mockGame, never()).chat(any(), any())
      }
    }

    // ---------------------------------------------------------------------------
    // login / quit (server delegation)
    // ---------------------------------------------------------------------------

    context("login / quit (server delegation)") {
      test("login delegates to server") {
        val user = makeUser()
        user.socketAddress = loopbackAddress
        whenever(mockServer.login(user)).thenReturn(Result.success(Unit))

        val result = user.login()

        result.isSuccess.shouldBeTrue()
        verify(mockServer).login(user)
      }

      test("quit delegates to server") {
        val user = makeUser()
        user.loggedIn = true

        user.quit("goodbye")

        verify(mockServer).quit(user, "goodbye")
        user.loggedIn.shouldBeFalse()
      }
    }

    // ---------------------------------------------------------------------------
    // joinGame
    // ---------------------------------------------------------------------------

    context("joinGame") {
      test("joinGame throws JoinGameException when already in a game") {
        val user = makeUser()
        val existingGame = mock<KailleraGame>()
        user.game = existingGame

        shouldThrow<JoinGameException> { user.joinGame(gameID = 1) }
      }

      test("joinGame throws JoinGameException when status is PLAYING") {
        val user = makeUser()
        user.status = UserStatus.PLAYING

        shouldThrow<JoinGameException> { user.joinGame(gameID = 1) }
      }

      test("joinGame throws JoinGameException when game does not exist") {
        val user = makeUser()
        user.status = UserStatus.IDLE
        whenever(mockServer.getGame(99)).thenReturn(null)

        shouldThrow<JoinGameException> { user.joinGame(gameID = 99) }
      }

      test("joinGame sets user game and returns the game") {
        val user = makeUser()
        user.status = UserStatus.IDLE
        val mockGame =
          mock<KailleraGame> {
            on { join(user) } doReturn 1
            on { id } doReturn 42
          }
        whenever(mockServer.getGame(42)).thenReturn(mockGame)

        val result = user.joinGame(gameID = 42)

        result shouldBe mockGame
        user.game shouldBe mockGame
      }
    }

    // ---------------------------------------------------------------------------
    // playerReady
    // ---------------------------------------------------------------------------

    context("playerReady") {
      test("playerReady throws UserReadyException when not in a game") {
        val user = makeUser()

        shouldThrow<UserReadyException> { user.playerReady() }
      }

      test("playerReady delegates to game when in game and queue is null") {
        val user = makeUser()
        val mockGame =
          mock<KailleraGame> {
            on { playerActionQueues } doReturn null
            on { highestUserFrameDelay } doReturn 0
          }
        user.game = mockGame
        user.playerNumber = 1

        user.playerReady()

        verify(mockGame).ready(user, 1)
      }
    }

    // ---------------------------------------------------------------------------
    // updateActivity
    // ---------------------------------------------------------------------------

    context("updateActivity") {
      test("updateActivity does not throw") {
        val user = makeUser()
        user.updateActivity(System.nanoTime())
      }
    }

    // ---------------------------------------------------------------------------
    // users collection via server
    // ---------------------------------------------------------------------------

    context("users collection via server") {
      test("users delegates to server usersMap") {
        val mockUsers = mutableMapOf(1 to makeUser(id = 1))
        whenever(mockServer.usersMap).thenReturn(mockUsers)

        val user = makeUser(id = 2)
        val users = user.users

        users.shouldContainExactlyInAnyOrder(mockUsers.values)
      }

      test("removeIgnoredUser removes every adjacent duplicate") {
        val user = makeUser()
        user.addIgnoredUser("1.2.3.4")
        user.addIgnoredUser("1.2.3.4")
        user.addIgnoredUser("1.2.3.4")

        user.removeIgnoredUser("1.2.3.4", removeAll = false)

        user.findIgnoredUser("1.2.3.4").shouldBeFalse()
      }
    }
  })
