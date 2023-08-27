package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.charsets.name
import java.util.concurrent.TimeUnit.HOURS
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.configuration.Configuration
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.CompiledFlags

@Serializable
data class ServerInfo(
  val name: String,
  val connectAddress: String,
  val connectPort: Int,
  val website: String,
  val location: String,
  val charset: String,
  val version: String,
  val isDevBuild: Boolean,
)

@Serializable data class CheckinRequest(val serverInfo: ServerInfo)

@Serializable data class CheckinResponse(val messagesToAdmins: List<String> = emptyList())

/**
 * Check in with the EmuLinker-K API to check for updates and other urgent messages for server
 * administrators, report high-level performance statistics to catch and fix regressions, and to
 * register the server with the master server lists (if enabled).
 */
class ServerCheckinTask
@Inject
constructor(
  private val publicServerInfo: PublicServerInformation,
  private val config: Configuration,
  private val httpClient: HttpClient
) : MasterListUpdateTask {

  override suspend fun touchMaster() {
    // The RPC is hosted using AWS Lambda, and there's no way to attach it to a custom URL without
    // using API Gateway, which costs money. By using the lambda URL directly and a placeholder URL
    // as a backup I save ~12-30 USD per year.
    val response: CheckinResponse? =
      touchMasterWithUrl(LAMBDA_PATH) ?: touchMasterWithUrl(BACKUP_PATH)
    if (response == null) {
      logger
        .atWarning()
        .atMostEvery(6, HOURS)
        .log(
          "Failed to touch EmuLinker-K central server. Check DEBUG-level logs for more info. Likely your server does not have outgoing HTTP permissions."
        )
      return
    }

    logger.atFine().log("CheckinResponse: %s", response)
    AppModule.messagesToAdmins = response.messagesToAdmins
  }

  /** @return Whether or not the RPC succeeded. */
  private suspend fun touchMasterWithUrl(url: String): CheckinResponse? {
    val request =
      CheckinRequest(
        ServerInfo(
          name = publicServerInfo.serverName,
          connectAddress = publicServerInfo.connectAddress,
          connectPort = config.getInt("controllers.connect.port"),
          website = publicServerInfo.website,
          location = publicServerInfo.location,
          charset = AppModule.charsetDoNotUse.name,
          version = CompiledFlags.PROJECT_VERSION,
          isDevBuild = CompiledFlags.DEBUG_BUILD
        )
      )

    val response: HttpResponse =
      try {
        httpClient.request(url) {
          this.method = HttpMethod.Post

          this.setBody(Json.encodeToString(request))

          this.timeout {
            this.connectTimeoutMillis = 5.seconds.inWholeMilliseconds
            this.requestTimeoutMillis = 5.seconds.inWholeMilliseconds
          }
        }
      } catch (e: Exception) {
        logger
          .atFine()
          .withCause(e)
          .log("Failed to check in with EmuLinker-K master API at URL %s", url)
        return null
      }
    if (response.status != HttpStatusCode.OK) return null

    return try {
      lenientJson.decodeFromString(response.bodyAsText())
    } catch (e: Exception) {
      logger
        .atWarning()
        .withCause(e)
        .atMostEvery(6, HOURS)
        .log("Failed to parse to CheckinResponse: %s", response.bodyAsText())
      null
    }
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    val lenientJson = Json { ignoreUnknownKeys = true }

    const val LAMBDA_PATH =
      "https://plzmuutb32kgr7jx73ettrtwga0ryzis.lambda-url.ap-northeast-1.on.aws/checkin"

    /** URL I will set up if [LAMBDA_PATH] goes bad. */
    const val BACKUP_PATH = "https://elk-api.12cb.dev/checkin"
  }
}
