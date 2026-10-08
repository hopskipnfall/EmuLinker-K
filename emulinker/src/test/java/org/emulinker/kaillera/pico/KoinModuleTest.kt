package org.emulinker.kaillera.pico

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.model.KailleraServer
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/** Guards against wiring mistakes: every production dependency must be resolvable from Koin. */
class KoinModuleTest {
  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun productionGraphResolves() {
    val koin = startKoin { modules(koinModule, ActionModule) }.koin

    assertThat(koin.get<KailleraServer>()).isNotNull()
    assertThat(koin.get<CombinedKailleraController>()).isNotNull()
  }
}
