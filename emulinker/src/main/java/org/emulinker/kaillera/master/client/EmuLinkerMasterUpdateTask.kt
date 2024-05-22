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
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraServer

class EmuLinkerMasterUpdateTask
@Inject
constructor(
  private val publicInfo: PublicServerInformation,
  private val config: Configuration,
  private val kailleraServer: KailleraServer,
) : MasterListUpdateTask {

  override fun reportStatus() {
    val url = URLBuilder(TOUCH_LIST_URL)
    with(url.parameters) {
      this.append("serverName", publicInfo.serverName)
      this.append("ipAddress", publicInfo.connectAddress)
      this.append("location", publicInfo.location)
      this.append("website", publicInfo.website)
      this.append("port", config.getInt("controllers.connect.port").toString())
      this.append("numUsers", kailleraServer.users.size.toString())
      this.append("maxUsers", kailleraServer.maxUsers.toString())
      this.append("numGames", kailleraServer.games.size.toString())
      this.append("maxGames", kailleraServer.maxGames.toString())
      // I want to use `releaseInfo.versionWithElkPrefix` here, but it's too long for the db schema
      // field, so we just write elk (lowercase in protest :P ).
      this.append("version", "elk")
    }

    val connection: HttpURLConnection = URL(url.buildString()).openConnection() as HttpURLConnection
    connection.setRequestMethod("GET")
    connection.setRequestProperty(
      "Waiting-games",
      kailleraServer.games
        .filter { it.status == GameStatus.WAITING }
        .joinToString(separator = "") {
          "${it.romName}|${it.owner.name}|${it.owner.clientType}|${it.players.size}/${it.maxUsers}|"
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

    private const val TOUCH_LIST_URL = "http://kaillerareborn.2manygames.fr/touch_list.php"

    private const val FETCH_LIST_URL = "http://kaillerareborn.2manygames.fr/server_list.php"
  }
}
