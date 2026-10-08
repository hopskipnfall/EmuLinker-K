package org.emulinker.kaillera.master

import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer

class MasterListStatsCollector : StatsCollector {
  private val startedGamesList = mutableListOf<String>()

  @Synchronized
  override fun markGameAsStarted(server: KailleraServer, game: KailleraGame) {
    startedGamesList.add(game.romName)
  }

  @Synchronized
  override fun drainStartedGames(): List<String> {
    val drained = startedGamesList.toList()
    startedGamesList.clear()
    return drained
  }
}
