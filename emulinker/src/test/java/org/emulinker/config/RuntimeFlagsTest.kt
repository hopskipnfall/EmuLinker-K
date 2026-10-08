package org.emulinker.config

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class RuntimeFlagsTest {
  @Test
  fun toString_doesNotLeakSecrets() {
    val text = buildFlags().toString()

    assertThat(text).doesNotContain("SECRET-TOKEN")
    assertThat(text).doesNotContain("SECRET-API-KEY")
  }

  private fun buildFlags() =
    RuntimeFlags(
      allowMultipleConnections = true,
      allowSinglePlayer = true,
      charset = Charsets.UTF_8,
      chatFloodTime = 0.milliseconds,
      allowedProtocols = listOf("0.83"),
      allowedConnectionTypes = listOf("1"),
      coreThreadPoolSize = 4,
      createGameFloodTime = 0.milliseconds,
      gameAutoFireSensitivity = 0,
      gameBufferSize = 12,
      idleTimeout = 1.minutes,
      keepAliveTimeout = 1.minutes,
      lagstatDuration = 1.minutes,
      language = "en",
      maxChatLength = 256,
      maxClientNameLength = 64,
      maxGameChatLength = 256,
      maxGameNameLength = 64,
      maxGames = 100,
      maxPing = 1000.milliseconds,
      maxQuitMessageLength = 256,
      maxUserNameLength = 31,
      maxUsers = 100,
      metricsEnabled = false,
      metricsLoggingFrequency = 1.minutes,
      serverAddress = "127.0.0.1",
      serverLocation = "Test",
      serverName = "Test Server",
      serverPort = 27888,
      serverWebsite = "",
      switchStatusBytesForBuggyClient = false,
      touchEmulinker = false,
      touchKaillera = false,
      twitterBroadcastDelay = 0.milliseconds,
      twitterDeletePostOnClose = false,
      twitterEnabled = false,
      twitterOAuthAccessToken = "SECRET-TOKEN",
      twitterOAuthAccessTokenSecret = "",
      twitterOAuthConsumerKey = "",
      twitterOAuthConsumerSecret = "SECRET-TOKEN",
      twitterPreventBroadcastNameSuffixes = emptyList(),
      v086BufferSize = 4096,
      surveyEnabled = false,
      surveyGameWhitelist = emptyList(),
      surveyApiEndpoint = "",
      surveyApiKey = "SECRET-API-KEY",
    )
}
