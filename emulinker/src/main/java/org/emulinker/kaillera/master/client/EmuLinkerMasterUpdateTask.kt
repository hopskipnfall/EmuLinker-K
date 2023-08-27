package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import org.apache.commons.configuration.Configuration
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo

class EmuLinkerMasterUpdateTask
@Inject
constructor(
  private val publicInfo: PublicServerInformation,
  private val config: Configuration,
  private val kailleraServer: KailleraServer,
  private val releaseInfo: ReleaseInfo,
  private val httpClient: HttpClient,
) : MasterListUpdateTask {

  override suspend fun touchMaster() {
    try {
      val response =
        httpClient.request(TOUCH_LIST_URL) {
          this.method = HttpMethod.Get

          this.timeout {
            this.connectTimeoutMillis = 5.seconds.inWholeMilliseconds
            this.requestTimeoutMillis = 5.seconds.inWholeMilliseconds
          }

          this.parameter("serverName", publicInfo.serverName)
          this.parameter("ipAddress", publicInfo.connectAddress)
          this.parameter("location", publicInfo.location)
          this.parameter("website", publicInfo.website)
          this.parameter("port", config.getInt("controllers.connect.port"))
          this.parameter("numUsers", kailleraServer.users.size)
          this.parameter("maxUsers", kailleraServer.maxUsers)
          this.parameter("numGames", kailleraServer.games.size)
          this.parameter("maxGames", kailleraServer.maxGames)
          this.parameter("version", releaseInfo.versionWithElkPrefix)

          this.header(
            "Waiting-games",
            kailleraServer.games
              .filter { it.status == GameStatus.WAITING }
              .joinToString(separator = "") {
                "${it.romName}|${it.owner.name}|${it.owner.clientType}|${it.players.size}/${it.maxUsers}|"
              }
          )
        }
      if (response.status != HttpStatusCode.OK) {
        logger
          .atWarning()
          .atMostEvery(6, TimeUnit.HOURS)
          .log("Failed to touch EmuLinker Master: %s", response)
      } else {
        logger.atFine().log("Touching EmuLinker Master done: %s", response)
      }
    } catch (e: Exception) {
      logger
        .atWarning()
        .atMostEvery(6, TimeUnit.HOURS)
        .withCause(e)
        .log("Failed to touch EmuLinker Master: %s")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val TOUCH_LIST_URL = "http://kaillerareborn.2manygames.fr/touch_list.php"
  }
}
