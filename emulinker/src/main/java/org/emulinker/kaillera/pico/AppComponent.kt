package org.emulinker.kaillera.pico

import com.codahale.metrics.MetricRegistry
import kotlinx.datetime.Clock
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.KailleraServerController
import org.emulinker.kaillera.master.client.MasterListUpdater
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NewAppComponent : KoinComponent {
  val configuration: Configuration by inject()
  val releaseInfo: ReleaseInfo by inject()
  val kailleraServerController: KailleraServerController by inject()
  val combinedKaillerController: CombinedKailleraController by inject()
  val kailleraServer: KailleraServer by inject()
  val masterListUpdater: MasterListUpdater by inject()
  val metricRegistry: MetricRegistry by inject()
  val flags: RuntimeFlags by inject()
  val clock: Clock by inject()
}
