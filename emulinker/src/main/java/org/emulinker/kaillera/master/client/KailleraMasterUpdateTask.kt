package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.http.URLBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
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
) : MasterListUpdateTask {

  override fun reportStatus() {
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

    val url = URLBuilder(TOUCH_LIST_URL)
    with(url.parameters) {
      this.append("servername", publicInfo.serverName)
      this.append("port", config.getInt("controllers.connect.port").toString())
      this.append("nbusers", kailleraServer.users.size.toString())
      this.append("maxconn", kailleraServer.maxUsers.toString())
      // I want to use `releaseInfo.versionWithElkPrefix` here, but it's too long for the db schema
      // field, so we just write elk (lowercase in protest :P ).
      this.append("version", "elk")
      this.append("nbgames", kailleraServer.games.size.toString())
      this.append("location", publicInfo.location)
      // If this doesn't "look right" to the server it will silently not show up in the server
      // list.
      this.append("ip", publicInfo.connectAddress)
      this.append("url", publicInfo.website)
    }

    val connection: HttpURLConnection = URL(url.buildString()).openConnection() as HttpURLConnection
    connection.setRequestMethod("GET")

    connection.setRequestProperty("Kaillera-games", createdGames.toString())
    connection.setRequestProperty(
      "Kaillera-wgames",
      kailleraServer.games
        .filter { it.status == GameStatus.WAITING }
        .joinToString(separator = "") {
          "${it.id}|${it.romName}|${it.owner.name}|${it.owner.clientType}|${it.players.size}|"
        }
    )

    // Get response code
    val responseCode: Int = connection.getResponseCode()

    // Process response
    if (responseCode == HttpURLConnection.HTTP_OK) {
      val response = StringBuilder()
      var line: String?
      val br = BufferedReader(InputStreamReader(connection.inputStream))
      while ((br.readLine().also { line = it }) != null) {
        response.append(line)
      }
      br.close()
      logger.atFine().log("Touching EmuLinker Master done: %s", response)
    } else {
      logger
        .atWarning()
        .atMostEvery(6, TimeUnit.HOURS)
        .log("Failed to touch EmuLinker Master: %d", responseCode)
    }

    // Disconnect the connection
    connection.disconnect()
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    private const val TOUCH_LIST_URL = "http://www.kaillera.com/touch_server.php"

    private const val FETCH_LIST_URL = "http://www.kaillera.com/raw_server_list2.php"
  }
}
