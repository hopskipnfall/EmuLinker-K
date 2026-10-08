package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.utils.io.charsets.name
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit.HOURS
import kotlin.String
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.CompiledFlags
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.ProtocolCharset

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
  val defaultLocale: String,
  val osName: String,
  val javaVersion: String,
  val javaVendor: String,
  val availableProcessors: Int,
  val language: String,
)

@Serializable data class CheckinRequest(val serverInfo: ServerInfo)

@Serializable data class CheckinResponse(val messagesToAdmins: List<String> = emptyList())

/**
 * Check in with the EmuLinker-K API to check for updates and other urgent messages for server
 * administrators, report high-level performance statistics to catch and fix regressions, and to
 * register the server with the master server lists (if enabled).
 */
class ServerCheckinTask(
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
    // This text comes from a remote server and is shown to every admin who logs in, so bound it.
    AppModule.messagesToAdmins =
      response.messagesToAdmins.take(MAX_ADMIN_MESSAGES).map { it.take(MAX_ADMIN_MESSAGE_LENGTH) }
  }

  /**
   * Sends the check-in request to [url].
   *
   * @return The server's response, or null if the request failed for any reason (so the caller can
   *   fall back to another URL).
   */
  private fun touchMasterWithUrl(url: URL): CheckinResponse? {
    val request =
      CheckinRequest(
        ServerInfo(
          name = publicServerInfo.serverName,
          connectAddress = publicServerInfo.connectAddress,
          connectPort = flags.serverPort,
          website = publicServerInfo.website,
          location = publicServerInfo.location,
          charset = ProtocolCharset.value.name,
          version = releaseInfo.versionWithElkPrefix,
          isDevBuild = CompiledFlags.DEBUG_BUILD,
          defaultLocale = Locale.getDefault().toString(),
          osName = System.getProperty("os.name"),
          javaVersion = System.getProperty("java.version"),
          javaVendor = System.getProperty("java.vendor"),
          availableProcessors = Runtime.getRuntime().availableProcessors(),
          language = flags.language,
        )
      )

    val connection = url.openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
      connection.doOutput = true
      connection.connectTimeout = 2_000 // milliseconds
      connection.readTimeout = 2_000 // milliseconds

      // The body must be encoded as UTF-8 explicitly: DataOutputStream.writeBytes() keeps only the
      // low byte of each character and mangled non-ASCII server names and locations.
      connection.outputStream.use {
        it.write(Json.encodeToString(request).toByteArray(Charsets.UTF_8))
      }

      val responseCode = connection.responseCode
      if (responseCode != HttpURLConnection.HTTP_OK) {
        logger.atWarning().log("Error: HTTP Response code - %d", responseCode)
        return null
      }

      // The response is a short JSON document; do not buffer an arbitrarily large body.
      val body =
        connection.inputStream.use { String(it.readNBytes(MAX_RESPONSE_BYTES), Charsets.UTF_8) }
      return try {
        lenientJson.decodeFromString<CheckinResponse?>(body)
      } catch (e: Exception) {
        logger
          .atWarning()
          .withCause(e)
          .atMostEvery(6, HOURS)
          .log("Failed to parse to CheckinResponse: %s", body)
        null
      }
    } catch (e: IOException) {
      // Timeouts, DNS failures, refused connections... Report as a failure instead of throwing so
      // the backup URL is tried.
      logger.atFine().withCause(e).log("Failed to reach %s", url.host)
      return null
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    val lenientJson = Json { ignoreUnknownKeys = true }

    const val MAX_RESPONSE_BYTES = 64 * 1024
    const val MAX_ADMIN_MESSAGES = 5
    const val MAX_ADMIN_MESSAGE_LENGTH = 500

    val LAMBDA_PATH =
      URL("https://plzmuutb32kgr7jx73ettrtwga0ryzis.lambda-url.ap-northeast-1.on.aws/checkin")

    /** URL I will set up if [LAMBDA_PATH] goes bad. */
    val BACKUP_PATH = URL("https://elk-api.12cb.dev/checkin")
  }
}
