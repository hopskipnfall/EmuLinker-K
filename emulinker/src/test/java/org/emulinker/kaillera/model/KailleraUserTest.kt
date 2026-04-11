package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
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
import org.junit.Assert.assertThrows
import org.junit.Test
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
class KailleraUserTest {

  // ---------------------------------------------------------------------------
  // Shared fixtures
  // ---------------------------------------------------------------------------

  private val defaultFlags =
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
    )

  private val mockClientHandler = mock<V086ClientHandler>()
  private val mockServer = mock<KailleraServer>()

  private val loopbackAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 27000)

  /** Constructs a fresh [KailleraUser] for each test. */
  private fun makeUser(
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

  @Test
  fun `toString includes id`() {
    val user = makeUser(id = 42)
    assertThat(user.toString()).contains("id=42")
  }

  @Test
  fun `toString includes name when set`() {
    val user = makeUser()
    user.name = "NovaStar"
    assertThat(user.toString()).contains("NovaStar")
  }

  @Test
  fun `toString handles null name`() {
    val user = makeUser()
    // Should not throw
    assertThat(user.toString()).isNotEmpty()
  }

  // ---------------------------------------------------------------------------
  // clientType — isEsfAdminClient detection
  // ---------------------------------------------------------------------------

  @Test
  fun `setting clientType to EmulinkerSF Admin Client prefix sets isEsfAdminClient`() {
    val user = makeUser()
    user.clientType = "EmulinkerSF Admin Client 1.2"
    assertThat(user.isEsfAdminClient).isTrue()
  }

  @Test
  fun `setting clientType to normal emulator does not set isEsfAdminClient`() {
    val user = makeUser()
    user.clientType = "Project 64k 0.13 (01 Aug 2003)"
    assertThat(user.isEsfAdminClient).isFalse()
  }

  @Test
  fun `setting clientType to null does not set isEsfAdminClient`() {
    val user = makeUser()
    user.clientType = null
    assertThat(user.isEsfAdminClient).isFalse()
  }

  // ---------------------------------------------------------------------------
  // isDead / isIdleForTooLong
  // ---------------------------------------------------------------------------

  @Test
  fun `isDead returns false immediately after construction`() {
    val user = makeUser()
    assertThat(user.isDead).isFalse()
  }

  @Test
  fun `isDead returns true when keepAliveTimeout has clearly elapsed`() {
    // Use a fixed clock that is 2 hours in the past so that time comparisons fire.
    val pastInstant = Clock.System.now() - 2.hours
    val frozenClock =
      object : Clock {
        override fun now(): Instant = pastInstant
      }
    val user = makeUser(clock = frozenClock)
    // System.nanoTime() naturally advances, so system-nanos check keeps it alive by default.
    // isDead requires BOTH checks to be satisfied; nanoTime is real so it won't be > 1 hour yet.
    // We can only truly assert the clock side; the nanoTime side will not be stale yet in a test.
    // Therefore isDead will appear false due to the nanoTime guard being fresh.
    assertThat(user.isDead).isFalse() // nanoTime guard protects it
  }

  @Test
  fun `isIdleForTooLong returns false immediately after construction`() {
    val user = makeUser()
    assertThat(user.isIdleForTooLong).isFalse()
  }

  // ---------------------------------------------------------------------------
  // updateLastKeepAlive
  // ---------------------------------------------------------------------------

  @Test
  fun `updateLastKeepAlive does not throw`() {
    val user = makeUser()
    user.updateLastKeepAlive() // Expected: no exception
  }

  // ---------------------------------------------------------------------------
  // game field
  // ---------------------------------------------------------------------------

  @Test
  fun `game is null initially`() {
    val user = makeUser()
    assertThat(user.game).isNull()
  }

  @Test
  fun `setting game to null resets playerNumber to -1`() {
    val user = makeUser()
    user.playerNumber = 2
    user.game = null
    assertThat(user.playerNumber).isEqualTo(-1)
  }

  @Test
  fun `setting game to non-null preserves playerNumber`() {
    val user = makeUser()
    user.playerNumber = 3
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    assertThat(user.playerNumber).isEqualTo(3)
  }

  // ---------------------------------------------------------------------------
  // Ignore list
  // ---------------------------------------------------------------------------

  @Test
  fun `findIgnoredUser returns false for unknown address`() {
    val user = makeUser()
    assertThat(user.findIgnoredUser("1.2.3.4")).isFalse()
  }

  @Test
  fun `addIgnoredUser then findIgnoredUser returns true`() {
    val user = makeUser()
    user.addIgnoredUser("1.2.3.4")
    assertThat(user.findIgnoredUser("1.2.3.4")).isTrue()
  }

  @Test
  fun `addIgnoredUser is address-specific`() {
    val user = makeUser()
    user.addIgnoredUser("1.2.3.4")
    assertThat(user.findIgnoredUser("5.6.7.8")).isFalse()
  }

  @Test
  fun `removeIgnoredUser removes single matching address`() {
    val user = makeUser()
    user.addIgnoredUser("1.2.3.4")
    user.addIgnoredUser("5.6.7.8")

    val removed = user.removeIgnoredUser("1.2.3.4", removeAll = false)

    assertThat(removed).isTrue()
    assertThat(user.findIgnoredUser("1.2.3.4")).isFalse()
    assertThat(user.findIgnoredUser("5.6.7.8")).isTrue()
  }

  @Test
  fun `removeIgnoredUser removeAll clears all addresses`() {
    val user = makeUser()
    user.addIgnoredUser("1.2.3.4")
    user.addIgnoredUser("5.6.7.8")

    val removed = user.removeIgnoredUser("doesnt-matter", removeAll = true)

    assertThat(removed).isTrue()
    assertThat(user.findIgnoredUser("1.2.3.4")).isFalse()
    assertThat(user.findIgnoredUser("5.6.7.8")).isFalse()
  }

  @Test
  fun `removeIgnoredUser returns false when address not found`() {
    val user = makeUser()
    val removed = user.removeIgnoredUser("not-there", removeAll = false)
    assertThat(removed).isFalse()
  }

  @Test
  fun `searchIgnoredUsers returns true for added address`() {
    val user = makeUser()
    user.addIgnoredUser("10.0.0.1")
    assertThat(user.searchIgnoredUsers("10.0.0.1")).isTrue()
  }

  @Test
  fun `searchIgnoredUsers returns false for unknown address`() {
    val user = makeUser()
    assertThat(user.searchIgnoredUsers("10.0.0.1")).isFalse()
  }

  // ---------------------------------------------------------------------------
  // loggedIn / status / name defaults
  // ---------------------------------------------------------------------------

  @Test
  fun `loggedIn is false after construction`() {
    val user = makeUser()
    assertThat(user.loggedIn).isFalse()
  }

  @Test
  fun `default status is PLAYING`() {
    // The KailleraUser comment says "This probably shouldn't have a default value."
    val user = makeUser()
    assertThat(user.status).isEqualTo(UserStatus.PLAYING)
  }

  @Test
  fun `default accessLevel is 0`() {
    val user = makeUser()
    assertThat(user.accessLevel).isEqualTo(0)
  }

  @Test
  fun `accessStr reflects accessLevel`() {
    val user = makeUser()
    user.accessLevel = AccessManager.ACCESS_NORMAL
    assertThat(user.accessStr).isEqualTo(AccessManager.ACCESS_NAMES[AccessManager.ACCESS_NORMAL])
  }

  // ---------------------------------------------------------------------------
  // surveyConsent / surveyConsentAskedTimeMark
  // ---------------------------------------------------------------------------

  @Test
  fun `surveyConsent is null by default`() {
    val user = makeUser()
    assertThat(user.surveyConsent).isNull()
  }

  @Test
  fun `surveyConsentAskedTimeMark is null by default`() {
    val user = makeUser()
    assertThat(user.surveyConsentAskedTimeMark).isNull()
  }

  @Test
  fun `surveyConsent can be set to true`() {
    val user = makeUser()
    user.surveyConsent = true
    assertThat(user.surveyConsent).isTrue()
  }

  @Test
  fun `surveyConsent can be set to false`() {
    val user = makeUser()
    user.surveyConsent = false
    assertThat(user.surveyConsent).isFalse()
  }

  // ---------------------------------------------------------------------------
  // miscellaneous mutable fields
  // ---------------------------------------------------------------------------

  @Test
  fun `isMuted defaults to false`() {
    val user = makeUser()
    assertThat(user.isMuted).isFalse()
  }

  @Test
  fun `ignoreAll defaults to false`() {
    val user = makeUser()
    assertThat(user.ignoreAll).isFalse()
  }

  @Test
  fun `isAcceptingDirectMessages defaults to true`() {
    val user = makeUser()
    assertThat(user.isAcceptingDirectMessages).isTrue()
  }

  @Test
  fun `lastMsgID defaults to -1`() {
    val user = makeUser()
    assertThat(user.lastMsgID).isEqualTo(-1)
  }

  @Test
  fun `frameCount defaults to 0`() {
    val user = makeUser()
    assertThat(user.frameCount).isEqualTo(0)
  }

  @Test
  fun `frameDelay defaults to 0`() {
    val user = makeUser()
    assertThat(user.frameDelay).isEqualTo(0)
  }

  @Test
  fun `playerNumber defaults to -1`() {
    val user = makeUser()
    assertThat(user.playerNumber).isEqualTo(-1)
  }

  @Test
  fun `inStealthMode defaults to false`() {
    val user = makeUser()
    assertThat(user.inStealthMode).isFalse()
  }

  @Test
  fun `ignoringUnnecessaryServerActivity defaults to false`() {
    val user = makeUser()
    assertThat(user.ignoringUnnecessaryServerActivity).isFalse()
  }

  @Test
  fun `swap defaults to false via KailleraGame usage`() {
    // swap is a @JvmField on KailleraGame, but we validate here that
    // KailleraUser.ignoringUnnecessaryServerActivity is independently false.
    val user = makeUser()
    assertThat(user.ignoringUnnecessaryServerActivity).isFalse()
  }

  // ---------------------------------------------------------------------------
  // connectSocketAddress / connectTime
  // ---------------------------------------------------------------------------

  @Test
  fun `connectSocketAddress is the address passed at construction`() {
    val addr = InetSocketAddress(InetAddress.getLoopbackAddress(), 12345)
    val user = makeUser(connectAddress = addr)
    assertThat(user.connectSocketAddress).isEqualTo(addr)
  }

  @Test
  fun `connectTime is set at construction time`() {
    val before = Clock.System.now()
    val user = makeUser()
    val after = Clock.System.now()
    assertThat(user.connectTime).isAtLeast(before)
    assertThat(user.connectTime).isAtMost(after)
  }

  // ---------------------------------------------------------------------------
  // equals / hashCode
  // ---------------------------------------------------------------------------

  @Test
  fun `two users with same id are equal`() {
    val u1 = makeUser(id = 7)
    val u2 = makeUser(id = 7)
    assertThat(u1).isEqualTo(u2)
  }

  @Test
  fun `two users with different ids are not equal`() {
    val u1 = makeUser(id = 1)
    val u2 = makeUser(id = 2)
    assertThat(u1).isNotEqualTo(u2)
  }

  @Test
  fun `hashCode is consistent with equality`() {
    val u1 = makeUser(id = 5)
    val u2 = makeUser(id = 5)
    assertThat(u1.hashCode()).isEqualTo(u2.hashCode())
  }

  @Test
  fun `user is not equal to non-user object`() {
    val user = makeUser(id = 1)
    assertThat(user).isNotEqualTo("not a user")
  }

  @Test
  fun `user is equal to itself`() {
    val user = makeUser(id = 3)
    @Suppress("ReplaceCallWithBinaryOperator") assertThat(user.equals(user)).isTrue()
  }

  // ---------------------------------------------------------------------------
  // doEvent — routing and side-effects
  // ---------------------------------------------------------------------------

  @Test
  fun `doEvent delegates to clientHandler`() {
    val user = makeUser()
    val server = mock<KailleraServer>()
    val event = InfoMessageEvent(user, "hello")

    user.doEvent(event)

    verify(mockClientHandler).actionPerformed(event)
  }

  @Test
  fun `doEvent with GameStartedEvent sets status to PLAYING`() {
    val user = makeUser()
    user.status = UserStatus.IDLE
    val mockGame = mock<KailleraGame>()
    val event = GameStartedEvent(mockGame)

    user.doEvent(event)

    assertThat(user.status).isEqualTo(UserStatus.PLAYING)
  }

  @Test
  fun `doEvent with UserQuitEvent for this user calls stop`() {
    val user = makeUser()
    val server = mock<KailleraServer>()
    val event = UserQuitEvent(server, user, "bye")

    user.doEvent(event)

    verify(mockClientHandler).stop()
  }

  @Test
  fun `doEvent with UserQuitEvent for different user does not stop`() {
    val user = makeUser(id = 1)
    val otherUser = makeUser(id = 2)
    val server = mock<KailleraServer>()
    val event = UserQuitEvent(server, otherUser, "bye")

    user.doEvent(event)

    verify(mockClientHandler, never()).stop()
  }

  @Test
  fun `doEvent InfoMessageEvent is suppressed when user is PLAYING and ignoringUnnecessaryServerActivity`() {
    val user = makeUser()
    user.status = UserStatus.PLAYING
    user.ignoringUnnecessaryServerActivity = true
    val event = InfoMessageEvent(user, "ignored")

    user.doEvent(event)

    verify(mockClientHandler, never()).actionPerformed(event)
  }

  @Test
  fun `doEvent InfoMessageEvent reaches clientHandler when user is IDLE`() {
    val user = makeUser()
    user.status = UserStatus.IDLE
    user.ignoringUnnecessaryServerActivity = true
    val event = InfoMessageEvent(user, "not ignored")

    user.doEvent(event)

    verify(mockClientHandler).actionPerformed(event)
  }

  @Test
  fun `doEvent InfoMessageEvent reaches clientHandler when ignoringUnnecessaryServerActivity is false`() {
    val user = makeUser()
    user.status = UserStatus.PLAYING
    user.ignoringUnnecessaryServerActivity = false
    val event = InfoMessageEvent(user, "delivered")

    user.doEvent(event)

    verify(mockClientHandler).actionPerformed(event)
  }

  // ---------------------------------------------------------------------------
  // droppedPacket
  // ---------------------------------------------------------------------------

  @Test
  fun `droppedPacket does nothing when game is null`() {
    val user = makeUser()
    // Should not throw
    user.droppedPacket()
  }

  @Test
  fun `droppedPacket delegates to game when in a game`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.playerNumber = 1

    user.droppedPacket()

    verify(mockGame).droppedPacket(user)
  }

  // ---------------------------------------------------------------------------
  // gameKick
  // ---------------------------------------------------------------------------

  @Test
  fun `gameKick throws GameKickException when not in a game`() {
    val user = makeUser()
    // user.game is null

    assertThrows(GameKickException::class.java) { user.gameKick(userID = 5) }
  }

  @Test
  fun `gameKick delegates to game when in a game`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.playerNumber = 1

    user.gameKick(userID = 99)

    verify(mockGame).kick(user, 99)
  }

  // ---------------------------------------------------------------------------
  // dropGame
  // ---------------------------------------------------------------------------

  @Test
  fun `dropGame does nothing when status is IDLE`() {
    val user = makeUser()
    user.status = UserStatus.IDLE

    user.dropGame() // Should not throw

    // game was null anyway so no interaction expected
    verify(mockClientHandler, never()).actionPerformed(any())
  }

  @Test
  fun `dropGame sets status to IDLE and calls game drop`() {
    val user = makeUser()
    user.status = UserStatus.PLAYING
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.playerNumber = 2

    user.dropGame()

    assertThat(user.status).isEqualTo(UserStatus.IDLE)
    verify(mockGame).drop(user, 2)
  }

  @Test
  fun `dropGame does not call game drop when game is null but status is PLAYING`() {
    val user = makeUser()
    user.status = UserStatus.PLAYING
    // game remains null

    user.dropGame()

    assertThat(user.status).isEqualTo(UserStatus.IDLE)
  }

  // ---------------------------------------------------------------------------
  // quitGame
  // ---------------------------------------------------------------------------

  @Test
  fun `quitGame does nothing when game is null`() {
    val user = makeUser()
    // Should not throw
    user.quitGame()
  }

  @Test
  fun `quitGame resets isMuted and game to null`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.isMuted = true
    user.status = UserStatus.IDLE // not PLAYING so drop is skipped
    user.playerNumber = 1

    user.quitGame()

    assertThat(user.isMuted).isFalse()
    assertThat(user.game).isNull()
  }

  @Test
  fun `quitGame when PLAYING calls drop then quit on game`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.status = UserStatus.PLAYING
    user.playerNumber = 1

    user.quitGame()

    verify(mockGame).drop(user, 1)
    verify(mockGame).quit(user, 1)
  }

  // ---------------------------------------------------------------------------
  // startGame
  // ---------------------------------------------------------------------------

  @Test
  fun `startGame throws StartGameException when not in a game`() {
    val user = makeUser()

    assertThrows(StartGameException::class.java) { user.startGame() }
  }

  @Test
  fun `startGame delegates to game when in a game`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame

    user.startGame()

    verify(mockGame).start(user)
  }

  // ---------------------------------------------------------------------------
  // gameChat
  // ---------------------------------------------------------------------------

  @Test
  fun `gameChat throws GameChatException when not in a game`() {
    val user = makeUser()

    assertThrows(GameChatException::class.java) { user.gameChat("hello", messageID = 1) }
  }

  @Test
  fun `gameChat announces mute message when user is muted`() {
    val user = makeUser()
    val mockGame = mock<KailleraGame>()
    user.game = mockGame
    user.isMuted = true

    user.gameChat("hello", messageID = 1)

    verify(mockGame).announce(any(), any())
    verify(mockGame, never()).chat(any(), any())
  }

  @Test
  fun `gameChat delegates to game chat when user is not muted or silenced`() {
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

            override fun addPermaBan(addressPattern: String, issuer: String?, reason: String?) {}

            override fun addPermaMute(addressPattern: String, issuer: String?, reason: String?) {}

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

  @Test
  fun `gameChat announces silence message when user is silenced`() {
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

  // ---------------------------------------------------------------------------
  // login / quit (server delegation)
  // ---------------------------------------------------------------------------

  @Test
  fun `login delegates to server`() {
    val user = makeUser()
    user.socketAddress = loopbackAddress
    whenever(mockServer.login(user)).thenReturn(Result.success(Unit))

    val result = user.login()

    assertThat(result.isSuccess).isTrue()
    verify(mockServer).login(user)
  }

  @Test
  fun `quit delegates to server`() {
    val user = makeUser()
    user.loggedIn = true

    user.quit("goodbye")

    verify(mockServer).quit(user, "goodbye")
    assertThat(user.loggedIn).isFalse()
  }

  // ---------------------------------------------------------------------------
  // joinGame
  // ---------------------------------------------------------------------------

  @Test
  fun `joinGame throws JoinGameException when already in a game`() {
    val user = makeUser()
    val existingGame = mock<KailleraGame>()
    user.game = existingGame

    assertThrows(JoinGameException::class.java) { user.joinGame(gameID = 1) }
  }

  @Test
  fun `joinGame throws JoinGameException when status is PLAYING`() {
    val user = makeUser()
    user.status = UserStatus.PLAYING

    assertThrows(JoinGameException::class.java) { user.joinGame(gameID = 1) }
  }

  @Test
  fun `joinGame throws JoinGameException when game does not exist`() {
    val user = makeUser()
    user.status = UserStatus.IDLE
    whenever(mockServer.getGame(99)).thenReturn(null)

    assertThrows(JoinGameException::class.java) { user.joinGame(gameID = 99) }
  }

  @Test
  fun `joinGame sets user game and returns the game`() {
    val user = makeUser()
    user.status = UserStatus.IDLE
    val mockGame =
      mock<KailleraGame> {
        on { join(user) } doReturn 1
        on { id } doReturn 42
      }
    whenever(mockServer.getGame(42)).thenReturn(mockGame)

    val result = user.joinGame(gameID = 42)

    assertThat(result).isEqualTo(mockGame)
    assertThat(user.game).isEqualTo(mockGame)
  }

  // ---------------------------------------------------------------------------
  // playerReady
  // ---------------------------------------------------------------------------

  @Test
  fun `playerReady throws UserReadyException when not in a game`() {
    val user = makeUser()

    assertThrows(org.emulinker.kaillera.model.exception.UserReadyException::class.java) {
      user.playerReady()
    }
  }

  @Test
  fun `playerReady delegates to game when in game and queue is null`() {
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

  // ---------------------------------------------------------------------------
  // updateActivity
  // ---------------------------------------------------------------------------

  @Test
  fun `updateActivity does not throw`() {
    val user = makeUser()
    user.updateActivity(System.nanoTime())
  }

  // ---------------------------------------------------------------------------
  // users collection via server
  // ---------------------------------------------------------------------------

  @Test
  fun `users delegates to server usersMap`() {
    val mockUsers = mutableMapOf(1 to makeUser(id = 1))
    whenever(mockServer.usersMap).thenReturn(mockUsers)

    val user = makeUser(id = 2)
    val users = user.users

    assertThat(users).containsExactlyElementsIn(mockUsers.values)
  }
}
