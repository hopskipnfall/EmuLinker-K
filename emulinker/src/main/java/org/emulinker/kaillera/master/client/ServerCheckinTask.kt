package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.utils.io.charsets.name
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit.HOURS
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.kaillera.release.ReleaseInfo

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
  private val releaseInfo: ReleaseInfo,
  private val flags: RuntimeFlags,
) : MasterListUpdateTask {

  override fun reportStatus() {
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
  private fun touchMasterWithUrl(url: URL): CheckinResponse? {

    val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setDoOutput(true) // idk if we need this

    val request =
      CheckinRequest(
        ServerInfo(
          name = publicServerInfo.serverName,
          connectAddress = publicServerInfo.connectAddress,
          connectPort = flags.serverPort,
          website = publicServerInfo.website,
          location = publicServerInfo.location,
          charset = AppModule.charsetDoNotUse.name,
          version = releaseInfo.versionWithElkPrefix,
          isDevBuild = CompiledFlags.DEBUG_BUILD
        )
      )

    // Write JSON data to the output stream
    val os = DataOutputStream(connection.outputStream)
    os.writeBytes(Json.encodeToString(request))
    os.flush()
    os.close()

    // Get response code
    val responseCode = connection.responseCode

    // Process response
    if (responseCode == HttpURLConnection.HTTP_OK) {
      val response = StringBuilder()
      var line: String?
      val br = BufferedReader(InputStreamReader(connection.inputStream))
      while ((br.readLine().also { line = it }) != null) {
        response.append(line)
      }
      br.close()

      // Disconnect the connection
      connection.disconnect()

      return try {
        lenientJson.decodeFromString<CheckinResponse?>(response.toString())
      } catch (e: Exception) {
        logger
          .atWarning()
          .withCause(e)
          .atMostEvery(6, HOURS)
          .log("Failed to parse to CheckinResponse: %s", response.toString())
        null
      }
    } else {
      logger.atWarning().log("Error: HTTP Response code - %d", responseCode)
      return null
    }
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    val lenientJson = Json { ignoreUnknownKeys = true }

    val LAMBDA_PATH =
      URL("https://plzmuutb32kgr7jx73ettrtwga0ryzis.lambda-url.ap-northeast-1.on.aws/checkin")

    /** URL I will set up if [LAMBDA_PATH] goes bad. */
    val BACKUP_PATH = URL("https://elk-api.12cb.dev/checkin")
  }
}
