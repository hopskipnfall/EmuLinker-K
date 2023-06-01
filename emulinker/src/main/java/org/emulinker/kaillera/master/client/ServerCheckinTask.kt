package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.charsets.name
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.emulinker.kaillera.controller.connectcontroller.ConnectController
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.CompiledFlags

@Serializable
data class ServerInfo(
  val name: String,
  val ip: String,
  val connectPort: Int,
  val website: String,
  val location: String,
  val charset: String,
  val version: String,
)

@Serializable data class CheckinRequest(val serverInfo: ServerInfo)

/**
 * Check in with the EmuLinker-K API to check for updates and other urgent messages for server
 * administrators, report high-level performance statistics to catch and fix regressions, and to
 * register the server with the master server lists (if enabled).
 */
class ServerCheckinTask
@Inject
constructor(
  private val publicServerInfo: PublicServerInformation,
  private val connectController: ConnectController,
) : MasterListUpdateTask {

  // TODO(nue): This should be a suspend method.
  override fun touchMaster() = runBlocking {
    // The RPC is hosted using AWS Lambda, and there's no way to attach it to a custom URL without
    // using API Gateway, which costs money. By using the lambda URL directly and a placeholder URL
    // as a backup I save ~12-30 USD per year.
    if (!touchMasterWithUrl(LAMBDA_PATH)) {
      touchMasterWithUrl(BACKUP_PATH)
    }
  }

  /** @return Whether or not the RPC succeeded. */
  private suspend fun touchMasterWithUrl(url: String): Boolean {
    val request =
      CheckinRequest(
        ServerInfo(
          name = publicServerInfo.serverName,
          ip = publicServerInfo.connectAddress,
          connectPort = connectController.boundPort!!,
          website = publicServerInfo.website,
          location = publicServerInfo.location,
          charset = AppModule.charsetDoNotUse.name,
          version = CompiledFlags.PROJECT_VERSION,
        )
      )

    val httpClient = HttpClient(CIO) { install(HttpTimeout) }
    httpClient.use { client ->
      val response: HttpResponse =
        try {
          client.request(url) {
            this.method = HttpMethod.Post

            this.setBody(Json.encodeToString(request))
            this.timeout { requestTimeoutMillis = 5.seconds.inWholeMilliseconds }
          }
        } catch (e: Exception) {
          logger
            .atFine()
            .withCause(e)
            .log("Failed to check in with EmuLinker-K master API at URL %s", url)
          return false
        }

      return response.status == HttpStatusCode.OK
    }
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    const val LAMBDA_PATH =
      "https://plzmuutb32kgr7jx73ettrtwga0ryzis.lambda-url.ap-northeast-1.on.aws/checkin"

    /** URL I will set up if [LAMBDA_PATH] goes bad. */
    const val BACKUP_PATH = "https://elk-api.12cb.dev/checkin"
  }
}
