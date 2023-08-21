package org.emulinker.kaillera.master.client

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ServerCheckinTaskTest {
  @get:Rule val logging = LoggingRule()

  private val runtimeFlags =
    RuntimeFlags(
      allowMultipleConnections = true,
      allowSinglePlayer = true,
      charset = Charsets.ISO_8859_1,
      chatFloodTime = 42,
      connectionTypes = emptyList(),
      coreThreadPoolSize = 5,
      createGameFloodTime = 42,
      gameAutoFireSensitivity = 4,
      gameBufferSize = 42,
      gameDesynchTimeouts = 42,
      gameTimeout = 42.seconds,
      idleTimeout = 42.seconds,
      improvedLagstatEnabled = true,
      keepAliveTimeout = 100.seconds,
      maxChatLength = 100,
      maxClientNameLength = 100,
      maxGameChatLength = 100,
      maxGameNameLength = 100,
      maxGames = 30,
      maxPing = 100,
      maxQuitMessageLength = 100,
      maxUserNameLength = 30,
      maxUsers = 30,
      metricsEnabled = false,
      metricsLoggingFrequency = 30.seconds,
      serverAddress = "",
      serverLocation = "Unknown",
      serverName = "Emulinker Server",
      serverWebsite = "",
      touchEmulinker = false,
      touchKaillera = false,
      twitterBroadcastDelay = 15.seconds,
      twitterDeletePostOnClose = false,
      twitterEnabled = false,
      twitterOAuthAccessToken = "",
      twitterOAuthAccessTokenSecret = "",
      twitterOAuthConsumerKey = "",
      twitterOAuthConsumerSecret = "",
      twitterPreventBroadcastNameSuffixes = emptyList(),
      v086BufferSize = 4096,
    )
  private val connectController = mock<Configuration>()

  @Before
  fun setUp() {
    AppModule.charsetDoNotUse = Charsets.ISO_8859_1

    whenever(connectController.getInt(eq("controllers.connect.port"))) doReturn 42
  }

  @Test
  fun goldenCase(): Unit = runTest {
    val mockEngine = MockEngine { respondOk(Json.encodeToString(CheckinResponse())) }

    ServerCheckinTask(
        PublicServerInformation(runtimeFlags),
        connectController,
        HttpClient(mockEngine) { install(HttpTimeout) }
      )
      .touchMaster()

    assertThat(mockEngine.requestHistory).hasSize(1)
  }

  @Test
  fun messagesAvailable(): Unit = runTest {
    val mockEngine = MockEngine {
      respondOk(Json.encodeToString(CheckinResponse(messagesToAdmins = listOf("hello", "world"))))
    }

    val target =
      ServerCheckinTask(
        PublicServerInformation(runtimeFlags),
        connectController,
        HttpClient(mockEngine) { install(HttpTimeout) }
      )
    target.touchMaster()

    assertThat(mockEngine.requestHistory).hasSize(1)

    assertThat(AppModule.messagesToAdmins).isNotNull()
    assertThat(AppModule.messagesToAdmins).containsExactly("hello", "world")
  }

  @Test
  fun responseContainsUnknownFields(): Unit = runTest {
    val mockEngine = MockEngine {
      respondOk("""{"messagesToAdmins":["hello","world"],"strangeField":"42"}""")
    }

    val target =
      ServerCheckinTask(
        PublicServerInformation(runtimeFlags),
        connectController,
        HttpClient(mockEngine) { install(HttpTimeout) }
      )
    target.touchMaster()

    assertThat(mockEngine.requestHistory).hasSize(1)

    assertThat(AppModule.messagesToAdmins).isNotNull()
    assertThat(AppModule.messagesToAdmins).containsExactly("hello", "world")
  }

  @Test
  fun missingAllFields(): Unit = runTest {
    val mockEngine = MockEngine { respondOk("{}") }

    val target =
      ServerCheckinTask(
        PublicServerInformation(runtimeFlags),
        connectController,
        HttpClient(mockEngine) { install(HttpTimeout) }
      )
    target.touchMaster()

    assertThat(mockEngine.requestHistory).hasSize(1)
  }
}
