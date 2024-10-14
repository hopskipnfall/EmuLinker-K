package org.emulinker.kaillera.master

import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer

class MasterListStatsCollector : StatsCollector {
  private val startedGamesList = mutableListOf<String>()

  @Synchronized
  override fun markGameAsStarted(server: KailleraServer, game: KailleraGame) {
    startedGamesList.add(game.romName)
  }

  @Synchronized override fun getStartedGamesList(): MutableList<String> = startedGamesList

  @Synchronized
  override fun clearStartedGamesList() {
    startedGamesList.clear()
  }
}
