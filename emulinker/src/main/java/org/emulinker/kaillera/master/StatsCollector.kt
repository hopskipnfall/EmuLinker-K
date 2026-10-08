package org.emulinker.kaillera.master

import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer

interface StatsCollector {
  fun markGameAsStarted(server: KailleraServer, game: KailleraGame)

  /** Returns the names of the games started since the last call, and forgets them. */
  fun drainStartedGames(): List<String>
}
