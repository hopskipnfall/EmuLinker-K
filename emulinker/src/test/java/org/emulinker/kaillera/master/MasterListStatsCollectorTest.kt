package org.emulinker.kaillera.master

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MasterListStatsCollectorTest :
  FunSpec({
    val server: KailleraServer = mock()

    fun gameNamed(name: String): KailleraGame = mock { on { romName } doReturn name }

    test("drainReturnsGamesStartedSinceLastDrainThenForgetsThem") {
      val collector = MasterListStatsCollector()
      collector.markGameAsStarted(server, gameNamed("smash"))
      collector.markGameAsStarted(server, gameNamed("mk3"))

      collector.drainStartedGames().shouldContainExactly("smash", "mk3")
      collector.drainStartedGames().shouldBeEmpty()

      collector.markGameAsStarted(server, gameNamed("sf2"))
      collector.drainStartedGames().shouldContainExactly("sf2")
    }
  })
