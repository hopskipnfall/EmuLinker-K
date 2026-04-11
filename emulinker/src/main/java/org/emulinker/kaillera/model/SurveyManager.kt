package org.emulinker.kaillera.model

import com.google.common.flogger.FluentLogger
import io.netty.buffer.ByteBuf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.emulinker.config.RuntimeFlags
import org.emulinker.proto.Event
import org.emulinker.proto.EventKt.fanOut
import org.emulinker.proto.EventKt.receivedGameData
import org.emulinker.proto.GameLog
import org.emulinker.proto.Player.PLAYER_FOUR
import org.emulinker.proto.Player.PLAYER_ONE
import org.emulinker.proto.Player.PLAYER_THREE
import org.emulinker.proto.Player.PLAYER_TWO
import org.emulinker.proto.event
import org.emulinker.util.EmuLang
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SurveyManager(private val game: KailleraGame) : KoinComponent {

  private val flags: RuntimeFlags by inject()

  val isSurveyEligibleForGame =
    flags.surveyEnabled && flags.surveyGameWhitelist.any { Regex(it).containsMatchIn(game.romName) }

  private var gameStartTimeMark: TimeMark? = null
  var lastSurveyAskedTimeMark: TimeMark? = null
  private var pendingSurveyGameLog: ByteArray? = null
  private var gameLogBuilder: GameLog.Builder? = null

  fun onGameStarted() {
    if (isSurveyEligibleForGame) {
      gameStartTimeMark = TimeSource.Monotonic.markNow()
    }
  }

  /** If eligible, ask the user for survey consent when they join the game. */
  fun onUserJoined(user: KailleraUser) {
    if (isSurveyEligibleForGame && user.surveyConsent == null) {
      game.announce(EmuLang.getString("Survey.Consent"), user)
      user.surveyConsentAskedTimeMark = TimeSource.Monotonic.markNow()
    }
  }

  /**
   * If a survey is active, check for a user pressing "start" once every three frames to trigger a
   * survey for all users.
   */
  fun onControllerInput(user: KailleraUser, data: ByteBuf, bytesPerAction: Int) {
    if (!isSurveyEligibleForGame || game.status != GameStatus.PLAYING) return
    if (user.frameCount % 3 != 0) return

    val gameStart = gameStartTimeMark ?: return
    if (gameStart.elapsedNow() <= SURVEY_GAME_START_DELAY) return

    val lastAsked = lastSurveyAskedTimeMark
    if (lastAsked != null && lastAsked.elapsedNow() <= SURVEY_COOLDOWN) return

    val eligiblePlayers = game.players.filter { it.surveyConsent == true }

    if (eligiblePlayers.isNotEmpty()) {
      if (checkStartPressed(data, bytesPerAction)) {
        pendingSurveyGameLog = gameLogBuilder?.build()?.toByteArray()
        for (player in eligiblePlayers) {
          game.announce("SURVEY: ${EmuLang.getString("Survey.Question", "10")}", player)
        }
        lastSurveyAskedTimeMark = TimeSource.Monotonic.markNow()
      }
    }
  }

  fun logReceivedGameData(playerNumber: Int, timestampNs: Long?) {
    val glb = gameLogBuilder ?: return
    glb.addEvents(
      event {
        this.timestampNs = checkNotNull(timestampNs) { "user's receivedGameDataNs is null!" }
        receivedGameData =
          when (playerNumber) {
            1 -> RECEIVED_FROM_P1
            2 -> RECEIVED_FROM_P2
            3 -> RECEIVED_FROM_P3
            4 -> RECEIVED_FROM_P4
            else -> throw IllegalStateException("Player number is out of bounds!")
          }
      }
    )
  }

  private fun checkStartPressed(data: ByteBuf, bytesPerAction: Int): Boolean {
    if (data.readableBytes() < 13) return false
    return (data.getByte(data.readerIndex() + 12).toInt() and 0b00010000) != 0
  }

  fun handleChat(user: KailleraUser, message: String): Boolean {
    if (!isSurveyEligibleForGame) return false

    // Check for consent response
    if (user.surveyConsent == null) {
      val askedTime = user.surveyConsentAskedTimeMark
      if (askedTime != null && askedTime.elapsedNow() <= SURVEY_CONSENT_TIMEOUT) {
        when (message.lowercase()) {
          "y",
          "yes" -> {
            user.surveyConsent = true
            game.announce(EmuLang.getString("Survey.ConsentYes"), user)
            return true
          }
          "n",
          "no" -> {
            user.surveyConsent = false
            game.announce(EmuLang.getString("Survey.ConsentNo"), user)
            return true
          }
        }
      }
    }

    // Check for survey response
    if (message.trim().matches("[1-3]".toRegex())) {
      val lastAsked = lastSurveyAskedTimeMark
      if (lastAsked != null && lastAsked.elapsedNow() <= MAX_SURVEY_RESPONSE_TIME) {
        reportSurveyResponse(user, message.trim())
        game.announce(EmuLang.getString("Survey.Thanks"), user)
        return true
      }
    }

    return false
  }

  private fun reportSurveyResponse(user: KailleraUser, response: String) {
    val surveyLog = pendingSurveyGameLog
    logger
      .atInfo()
      .log(
        "Reporting survey response for %s: %s (Proto data size: %d)",
        user.name,
        response,
        surveyLog?.size ?: 0,
      )
    // TODO: Send response and surveyLog to a server for analysis.
  }

  /**
   * Determines if the game log builder should active capture telemetry. To save memory, telemetry
   * is ONLY buffered during the SURVEY_TELEMETRY_WINDOW (5 mins) immediately preceding a potential
   * survey prompt limit.
   */
  private fun shouldRecordTelemetry(): Boolean {
    if (!isSurveyEligibleForGame || game.status != GameStatus.PLAYING) return false
    val gameStart = gameStartTimeMark ?: return false

    val needsToRecordForFirstSurvey =
      gameStart.elapsedNow() >= (SURVEY_GAME_START_DELAY - SURVEY_TELEMETRY_WINDOW)

    val timeLimitSatisfied =
      when (val lastAsked = lastSurveyAskedTimeMark) {
        null -> needsToRecordForFirstSurvey
        else -> lastAsked.elapsedNow() >= (SURVEY_COOLDOWN - SURVEY_TELEMETRY_WINDOW)
      }

    return timeLimitSatisfied && game.players.any { it.surveyConsent == true }
  }

  fun updateDrift(nowNs: Long, lagstatData: Event.LagstatSummary?) {
    if (!isSurveyEligibleForGame) return

    if (shouldRecordTelemetry() && gameLogBuilder == null) {
      gameLogBuilder = GameLog.newBuilder()
    } else if (!shouldRecordTelemetry()) {
      gameLogBuilder = null
    }

    if (pendingSurveyGameLog != null) {
      val lastAsked = lastSurveyAskedTimeMark
      val anyoneStillResponding =
        lastAsked != null &&
          lastAsked.elapsedNow() <= MAX_SURVEY_RESPONSE_TIME &&
          game.players.any { it.surveyConsent == true }
      if (!anyoneStillResponding) {
        pendingSurveyGameLog = null
      }
    }

    val glb = gameLogBuilder
    if (glb != null) {
      glb.addEvents(
        event {
          timestampNs = nowNs
          fanOut = FAN_OUT
        }
      )

      if (lagstatData != null) {
        glb.addEvents(
          event {
            timestampNs = nowNs
            lagstatSummary = lagstatData
          }
        )
      }

      // Prune the game log to a 5-minute rolling window to prevent unbounded memory growth.
      val fiveMinsAgoNs = nowNs - SURVEY_TELEMETRY_WINDOW.inWholeNanoseconds
      val recentEvents = glb.eventsList.takeLastWhile { it.timestampNs >= fiveMinsAgoNs }
      if (recentEvents.size < glb.eventsCount) {
        glb.clearEvents()
        glb.addAllEvents(recentEvents)
      }
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    val SURVEY_CONSENT_TIMEOUT = 4.minutes
    val MAX_SURVEY_RESPONSE_TIME = 1.5.minutes
    val SURVEY_GAME_START_DELAY = 8.minutes
    val SURVEY_COOLDOWN = 10.minutes
    val SURVEY_TELEMETRY_WINDOW = 5.minutes

    private val FAN_OUT = fanOut {}
    private val RECEIVED_FROM_P1 = receivedGameData { receivedFrom = PLAYER_ONE }
    private val RECEIVED_FROM_P2 = receivedGameData { receivedFrom = PLAYER_TWO }
    private val RECEIVED_FROM_P3 = receivedGameData { receivedFrom = PLAYER_THREE }
    private val RECEIVED_FROM_P4 = receivedGameData { receivedFrom = PLAYER_FOUR }
  }
}
