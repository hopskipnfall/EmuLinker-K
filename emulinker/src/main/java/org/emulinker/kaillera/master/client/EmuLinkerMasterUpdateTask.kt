package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import io.ktor.http.URLBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo

class EmuLinkerMasterUpdateTask(
  private val publicInfo: PublicServerInformation,
  private val kailleraServer: KailleraServer,
  private val flags: RuntimeFlags,
  private val releaseInfo: ReleaseInfo,
) : MasterListUpdateTask {

  override fun reportStatus() {
    val url = URLBuilder(TOUCH_LIST_URL)
    with(url.parameters) {
      this.append("serverName", publicInfo.serverName)
      this.append("ipAddress", publicInfo.connectAddress)
      this.append("location", publicInfo.location)
      this.append("website", publicInfo.website)
      this.append("port", flags.serverPort.toString())
      this.append("numUsers", kailleraServer.usersMap.values.size.toString())
      this.append("maxUsers", flags.maxUsers.toString())
      this.append("numGames", kailleraServer.gamesMap.values.size.toString())
      this.append("maxGames", flags.maxGames.toString())
      // The list only supports max 8 character versions.
      this.append("version", releaseInfo.versionWithElkPrefix.take(8))
    }

    val connection: HttpURLConnection = URL(url.buildString()).openConnection() as HttpURLConnection
    connection.setRequestMethod("GET")
    connection.setRequestProperty(
      "Waiting-games",
      kailleraServer.gamesMap.values
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
