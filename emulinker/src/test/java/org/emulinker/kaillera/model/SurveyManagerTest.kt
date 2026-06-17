package org.emulinker.kaillera.model

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.emulinker.config.RuntimeFlags
import org.emulinker.proto.GameLog
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SurveyManagerTest {

  private val mockFlags =
    mock<RuntimeFlags> {
      on { surveyEnabled } doReturn true
      on { surveyGameWhitelist } doReturn listOf("smash", "tekken")
    }

  // A user who has not yet given consent (surveyConsent == null, no timemark set).
  private val pendingConsentUser: KailleraUser = mock {
    on { frameCount } doReturn 3
    on { surveyConsent } doReturn null
    on { surveyConsentAskedTimeMark } doReturn null
  }

  // A user who has positively consented to surveys.
  private val consentedUser: KailleraUser = mock {
    on { frameCount } doReturn 3
    on { surveyConsent } doReturn true
    on { surveyConsentAskedTimeMark } doReturn TimeSource.Monotonic.markNow()
  }

  private val mockGame =
    mock<KailleraGame> {
      on { romName } doReturn "smash bros"
      on { status } doReturn GameStatus.PLAYING
      on { players } doReturn mutableListOf(pendingConsentUser)
    }

  @BeforeTest
  fun setUp() {
    startKoin { modules(module { single { mockFlags } }) }
  }

  @AfterTest
  fun tearDown() {
    stopKoin()
  }

  // ── Eligibility ──────────────────────────────────────────────────────────────

  @Test
  fun `isSurveyEligibleForGame is true when romName matches whitelist`() {
    val manager = SurveyManager(mockGame)
    assertThat(manager.isSurveyEligibleForGame).isTrue()
  }

  @Test
  fun `isSurveyEligibleForGame is false when romName does not match whitelist`() {
    val ineligibleGame = mock<KailleraGame> { on { romName } doReturn "mario kart" }
    val manager = SurveyManager(ineligibleGame)
    assertThat(manager.isSurveyEligibleForGame).isFalse()
  }

  @Test
  fun `isSurveyEligibleForGame is false when surveyEnabled is false`() {
    val disabledFlags =
      mock<RuntimeFlags> {
        on { surveyEnabled } doReturn false
        on { surveyGameWhitelist } doReturn listOf("smash")
      }
    stopKoin()
    startKoin { modules(module { single { disabledFlags } }) }

    val manager = SurveyManager(mockGame)
    assertThat(manager.isSurveyEligibleForGame).isFalse()
  }

  // ── onUserJoined ─────────────────────────────────────────────────────────────

  @Test
  fun `onUserJoined announces consent prompt and sets timemark for unconsenteed user`() {
    val manager = SurveyManager(mockGame)
    manager.onUserJoined(pendingConsentUser)

    verify(mockGame).announce(any(), org.mockito.kotlin.eq(pendingConsentUser))
    verify(pendingConsentUser).surveyConsentAskedTimeMark = any()
  }

  @Test
  fun `onUserJoined does nothing when game is not eligible`() {
    val ineligibleGame = mock<KailleraGame> { on { romName } doReturn "mario kart" }
    val manager = SurveyManager(ineligibleGame)
    manager.onUserJoined(pendingConsentUser)

    verify(ineligibleGame, never()).announce(any(), any())
  }

  @Test
  fun `onUserJoined does not re-prompt a user who already consented`() {
    val manager = SurveyManager(mockGame)
    manager.onUserJoined(consentedUser)

    verify(mockGame, never()).announce(any(), org.mockito.kotlin.eq(consentedUser))
  }

  // ── handleChat — consent ──────────────────────────────────────────────────────

  @Test
  fun `handleChat returns false for ineligible game`() {
    val ineligibleGame = mock<KailleraGame> { on { romName } doReturn "mario kart" }
    val manager = SurveyManager(ineligibleGame)

    assertThat(manager.handleChat(pendingConsentUser, "yes")).isFalse()
  }

  @Test
  fun `handleChat accepts yes consent when timemark is set and within timeout`() {
    // Give the user a timemark that is fresh (i.e. just now).
    val freshMark: TimeMark = TimeSource.Monotonic.markNow()
    val user: KailleraUser = mock {
      on { surveyConsent } doReturn null
      on { surveyConsentAskedTimeMark } doReturn freshMark
    }
    val manager = SurveyManager(mockGame)

    val handled = manager.handleChat(user, "yes")
    assertThat(handled).isTrue()
    verify(user).surveyConsent = true
  }

  @Test
  fun `handleChat accepts y shorthand for yes consent`() {
    val freshMark: TimeMark = TimeSource.Monotonic.markNow()
    val user: KailleraUser = mock {
      on { surveyConsent } doReturn null
      on { surveyConsentAskedTimeMark } doReturn freshMark
    }
    val manager = SurveyManager(mockGame)

    val handled = manager.handleChat(user, "y")
    assertThat(handled).isTrue()
    verify(user).surveyConsent = true
  }

  @Test
  fun `handleChat accepts no consent when timemark is fresh`() {
    val freshMark: TimeMark = TimeSource.Monotonic.markNow()
    val user: KailleraUser = mock {
      on { surveyConsent } doReturn null
      on { surveyConsentAskedTimeMark } doReturn freshMark
    }
    val manager = SurveyManager(mockGame)

    val handled = manager.handleChat(user, "no")
    assertThat(handled).isTrue()
    verify(user).surveyConsent = false
  }

  @Test
  fun `handleChat does not accept consent when timemark is null`() {
    // surveyConsentAskedTimeMark is null => consent window hasn't opened
    val user: KailleraUser = mock {
      on { surveyConsent } doReturn null
      on { surveyConsentAskedTimeMark } doReturn null
    }
    val manager = SurveyManager(mockGame)

    val handled = manager.handleChat(user, "yes")
    assertThat(handled).isFalse()
    verify(user, never()).surveyConsent = any()
  }

  // ── handleChat — survey response ──────────────────────────────────────────────

  @Test
  fun `handleChat accepts a numeric rating when survey was recently asked`() {
    val manager = SurveyManager(mockGame)
    // Simulate a survey having just been triggered.
    manager.lastSurveyAskedTimeMark = TimeSource.Monotonic.markNow()

    val handled = manager.handleChat(consentedUser, "2")
    assertThat(handled).isTrue()
  }

  @Test
  fun `handleChat ignores a numeric rating when lastSurveyAskedTimeMark is null`() {
    val manager = SurveyManager(mockGame) // lastSurveyAskedTimeMark stays null

    val handled = manager.handleChat(consentedUser, "2")
    assertThat(handled).isFalse()
  }

  @Test
  fun `handleChat returns false for unrelated chat messages`() {
    val manager = SurveyManager(mockGame)

    assertThat(manager.handleChat(consentedUser, "gg wp")).isFalse()
  }

  // ── onControllerInput ─────────────────────────────────────────────────────────

  @Test
  fun `onControllerInput bails early when game status is WAITING`() {
    val waitingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.WAITING
        on { players } doReturn mutableListOf(consentedUser)
      }
    val manager = SurveyManager(waitingGame)
    manager.onGameStarted() // sets gameStartTimeMark

    val data = Unpooled.buffer(16).apply { writeZero(16) }
    // Should not throw and should not announce anything.
    manager.onControllerInput(consentedUser, data, 2)
    verify(waitingGame, never()).announce(any(), any())
    data.release()
  }

  @Test
  fun `onControllerInput checks start button and triggers survey when frameCount is a multiple of 3`() {
    val user: KailleraUser = mock {
      on { frameCount } doReturn 6 // 6 is multiple of 3
      on { surveyConsent } doReturn true
    }
    val playingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.PLAYING
        on { players } doReturn mutableListOf(user)
      }

    val manager = SurveyManager(playingGame)

    // Use reflection to set gameStartTimeMark to 9 minutes ago
    val field = SurveyManager::class.java.getDeclaredField("gameStartTimeMark")
    field.isAccessible = true
    field.set(manager, TimeSource.Monotonic.markNow() - 9.minutes)

    // N64 controller data layout with start bit set at byte 12 (0x10)
    val data =
      Unpooled.buffer(24).apply {
        writeZero(12)
        writeByte(0x10) // start button pressed
        writeZero(11)
      }

    manager.onControllerInput(user, data, 2)

    verify(playingGame).announce(any(), org.mockito.kotlin.eq(user))
    data.release()
  }

  @Test
  fun `onControllerInput does not check start button when frameCount is not a multiple of 3`() {
    val user: KailleraUser = mock {
      on { frameCount } doReturn 7 // 7 is not a multiple of 3
      on { surveyConsent } doReturn true
    }
    val playingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.PLAYING
        on { players } doReturn mutableListOf(user)
      }

    val manager = SurveyManager(playingGame)

    // Use reflection to set gameStartTimeMark to 9 minutes ago
    val field = SurveyManager::class.java.getDeclaredField("gameStartTimeMark")
    field.isAccessible = true
    field.set(manager, TimeSource.Monotonic.markNow() - 9.minutes)

    // N64 controller data layout with start bit set at byte 12 (0x10)
    val data =
      Unpooled.buffer(24).apply {
        writeZero(12)
        writeByte(0x10) // start button pressed
        writeZero(11)
      }

    manager.onControllerInput(user, data, 2)

    // Should return early and NOT trigger survey (never announce)
    verify(playingGame, never()).announce(any(), any())
    data.release()
  }

  // ── Telemetry & Pruning ──────────────────────────────────────────────────────

  @Test
  fun `telemetry is not recorded before 3 minutes from game start`() {
    val user = consentedUser
    val playingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.PLAYING
        on { players } doReturn mutableListOf(user)
      }
    val manager = SurveyManager(playingGame)
    manager.onGameStarted()

    // Game started 2 minutes ago
    val field = SurveyManager::class.java.getDeclaredField("gameStartTimeMark")
    field.isAccessible = true
    field.set(manager, TimeSource.Monotonic.markNow() - 2.minutes)

    // Call updateDrift to run state machine.
    manager.updateDrift(System.nanoTime(), null)

    // Call logReceivedGameData. It should be ignored because telemetry is not recording.
    manager.logReceivedGameData(1, System.nanoTime())

    // Set gameStartTimeMark to 9 minutes ago so onControllerInput passes the delay check
    field.set(manager, TimeSource.Monotonic.markNow() - 9.minutes)

    val data =
      Unpooled.buffer(24).apply {
        writeZero(12)
        writeByte(0x10) // start button pressed
        writeZero(11)
      }

    manager.onControllerInput(user, data, 2)

    // Retrieve pendingSurveyGameLog
    val pendingLogField = SurveyManager::class.java.getDeclaredField("pendingSurveyGameLog")
    pendingLogField.isAccessible = true
    val bytes = pendingLogField.get(manager) as ByteArray?

    assertThat(bytes).isNotNull()
    val gameLog = GameLog.parseFrom(bytes!!)
    assertThat(gameLog.eventsCount).isEqualTo(0)
    data.release()
  }

  @Test
  fun `telemetry is recorded between 3 and 8 minutes from game start`() {
    val user = consentedUser
    val playingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.PLAYING
        on { players } doReturn mutableListOf(user)
      }
    val manager = SurveyManager(playingGame)
    manager.onGameStarted()

    // Game started 4 minutes ago (satisfies needsToRecordForFirstSurvey: 4 >= 3)
    val field = SurveyManager::class.java.getDeclaredField("gameStartTimeMark")
    field.isAccessible = true
    field.set(manager, TimeSource.Monotonic.markNow() - 4.minutes)

    manager.updateDrift(System.nanoTime(), null) // Appends 1 fanOut event

    // Call logReceivedGameData. It should be recorded.
    manager.logReceivedGameData(1, System.nanoTime()) // Appends 1 receivedGameData event

    // Set gameStartTimeMark to 9 minutes ago so onControllerInput passes the delay check
    field.set(manager, TimeSource.Monotonic.markNow() - 9.minutes)

    val data =
      Unpooled.buffer(24).apply {
        writeZero(12)
        writeByte(0x10) // start button pressed
        writeZero(11)
      }

    manager.onControllerInput(user, data, 2)

    val pendingLogField = SurveyManager::class.java.getDeclaredField("pendingSurveyGameLog")
    pendingLogField.isAccessible = true
    val bytes = pendingLogField.get(manager) as ByteArray?

    assertThat(bytes).isNotNull()
    val gameLog = GameLog.parseFrom(bytes!!)

    // Should contain 2 events: 1 fanOut and 1 receivedGameData
    assertThat(gameLog.eventsCount).isEqualTo(2)
    data.release()
  }

  @Test
  fun `telemetry older than 5 minutes is pruned`() {
    val user = consentedUser
    val playingGame =
      mock<KailleraGame> {
        on { romName } doReturn "smash"
        on { status } doReturn GameStatus.PLAYING
        on { players } doReturn mutableListOf(user)
      }
    val manager = SurveyManager(playingGame)
    manager.onGameStarted()

    // Game started 4 minutes ago
    val field = SurveyManager::class.java.getDeclaredField("gameStartTimeMark")
    field.isAccessible = true
    field.set(manager, TimeSource.Monotonic.markNow() - 4.minutes)

    // Event 1 (timestampNs = 0L)
    manager.updateDrift(0L, null)
    manager.logReceivedGameData(1, 0L)

    // Event 2 (timestampNs = 2 mins)
    manager.updateDrift(2.minutes.inWholeNanoseconds, null)
    manager.logReceivedGameData(1, 2.minutes.inWholeNanoseconds)

    // Event 3 (timestampNs = 6 mins) -> Event 1 is now older than 5 mins (6 - 5 = 1 min), so it
    // should be pruned.
    // Event 2 is 4 mins old, so it should be kept.
    manager.updateDrift(6.minutes.inWholeNanoseconds, null)

    // Set gameStartTimeMark to 10 minutes ago so onControllerInput passes the delay check
    field.set(manager, TimeSource.Monotonic.markNow() - 10.minutes)

    val data =
      Unpooled.buffer(24).apply {
        writeZero(12)
        writeByte(0x10) // start button pressed
        writeZero(11)
      }

    manager.onControllerInput(user, data, 2)

    val pendingLogField = SurveyManager::class.java.getDeclaredField("pendingSurveyGameLog")
    pendingLogField.isAccessible = true
    val bytes = pendingLogField.get(manager) as ByteArray?

    assertThat(bytes).isNotNull()
    val gameLog = GameLog.parseFrom(bytes!!)

    // Check that none of the events in gameLog have timestampNs < 1 minute (6 mins - 5 mins)
    val oneMinAgoNs = 1.minutes.inWholeNanoseconds
    for (event in gameLog.eventsList) {
      assertThat(event.timestampNs).isAtLeast(oneMinAgoNs)
    }
    data.release()
  }
}
