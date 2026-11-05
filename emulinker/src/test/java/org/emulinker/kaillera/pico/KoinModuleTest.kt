package org.emulinker.kaillera.pico

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.model.KailleraServer
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/** Guards against wiring mistakes: every production dependency must be resolvable from Koin. */
class KoinModuleTest :
  FunSpec({
    afterEach { stopKoin() }

    test("productionGraphResolves") {
      val koin = startKoin { modules(koinModule, ActionModule) }.koin

      koin.get<KailleraServer>().shouldNotBeNull()
      koin.get<CombinedKailleraController>().shouldNotBeNull()
    }
  })
