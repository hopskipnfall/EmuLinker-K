package org.emulinker.kaillera.pico

import com.google.common.flogger.FluentLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class VersionConfig(val version: String, val message: String)

@Serializable data class VersionUpdatePromptConfig(val versionConfigs: Array<VersionConfig>)

object UpdateChecker {
  private val logger = FluentLogger.forEnclosingClass()

  suspend fun fetchUpdateConfig(): VersionUpdatePromptConfig? {
    val httpClient = HttpClient(CIO) { install(HttpTimeout) }
    httpClient.use { client ->
      val response: HttpResponse =
        try {
          client.request(
            "https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/emulinker/conf/update_messages.json"
          ) {
            this.method = HttpMethod.Get
            this.timeout { requestTimeoutMillis = 5.seconds.inWholeMilliseconds }
          }
        } catch (e: Exception) {
          return null
        }

      return when {
        response.status != HttpStatusCode.OK -> {
          null
        }
        else -> {
          try {
            Json.decodeFromString(response.bodyAsText())
          } catch (e: Exception) {
            logger
              .atWarning()
              .withCause(e)
              .log("Failed to deserialize update request: %s", response.bodyAsText())
            null
          }
        }
      }
    }
  }
}
