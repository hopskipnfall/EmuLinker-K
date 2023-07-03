package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.http.HttpStatusCode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import org.apache.commons.configuration.Configuration
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraServer

class KailleraMasterUpdateTask
@Inject
constructor(
  private val publicInfo: PublicServerInformation,
  private val config: Configuration,
  private val kailleraServer: KailleraServer,
  private val statsCollector: StatsCollector,
  private val httpClient: HttpClient,
) : MasterListUpdateTask {

  override suspend fun touchMaster() {
    val createdGamesList = statsCollector.getStartedGamesList()

    val createdGames = StringBuilder()
    synchronized(createdGamesList) {
      val iter = createdGamesList.iterator()
      while (iter.hasNext()) {
        createdGames.append(iter.next())
        createdGames.append("|")
      }
      createdGamesList.clear()
    }

    try {
      val response =
        httpClient.request(TOUCH_LIST_URL) {
          this.method = io.ktor.http.HttpMethod.Get

          this.timeout {
            this.connectTimeoutMillis = 5.seconds.inWholeMilliseconds
            this.requestTimeoutMillis = 5.seconds.inWholeMilliseconds
          }

          this.parameter("servername", publicInfo.serverName)
          this.parameter("port", config.getInt("controllers.connect.port"))
          this.parameter("nbusers", kailleraServer.users.size)
          this.parameter("maxconn", kailleraServer.maxUsers)
          // I want to use `releaseInfo.versionWithElkPrefix` here, but for some reason this RPC
          // fails to write to the db. So we just write elk (lowercase in protest :P ).
          this.parameter("version", "elk")
          this.parameter("nbgames", kailleraServer.games.size)
          this.parameter("location", publicInfo.location)
          // If this doesn't "look right" to the server it will silently not show up in the server
          // list.
          this.parameter("ip", publicInfo.connectAddress)
          this.parameter("url", publicInfo.website)

          this.header("Kaillera-games", createdGames.toString())
          this.header(
            "Kaillera-wgames",
            kailleraServer.games
              .filter { it.status == GameStatus.WAITING }
              .joinToString(separator = "") {
                "${it.id}|${it.romName}|${it.owner.name}|${it.owner.clientType}|${it.players.size}|"
              }
          )
        }
      if (response.status != HttpStatusCode.OK) {
        logger
          .atWarning()
          .atMostEvery(6, TimeUnit.HOURS)
          .log("Failed to touch Kaillera Master: %s", response)
      } else {
        logger.atFine().log("Touching Kaillera Master done: %s", response)
      }
    } catch (e: Exception) {
      logger
        .atWarning()
        .atMostEvery(6, TimeUnit.HOURS)
        .withCause(e)
        .log("Failed to touch Kaillera Master")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val TOUCH_LIST_URL = "http://www.kaillera.com/touch_server.php"
  }
}
