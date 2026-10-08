package org.emulinker.kaillera.master

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraServer
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MasterListStatsCollectorTest {
  private val server: KailleraServer = mock()

  private fun gameNamed(name: String): KailleraGame = mock { on { romName } doReturn name }

  @Test
  fun drainReturnsGamesStartedSinceLastDrainThenForgetsThem() {
    val collector = MasterListStatsCollector()
    collector.markGameAsStarted(server, gameNamed("smash"))
    collector.markGameAsStarted(server, gameNamed("mk3"))

    assertThat(collector.drainStartedGames()).containsExactly("smash", "mk3").inOrder()
    assertThat(collector.drainStartedGames()).isEmpty()

    collector.markGameAsStarted(server, gameNamed("sf2"))
    assertThat(collector.drainStartedGames()).containsExactly("sf2")
  }
}
