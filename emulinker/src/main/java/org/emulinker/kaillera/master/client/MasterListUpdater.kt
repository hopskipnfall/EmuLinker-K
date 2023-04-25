package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import java.io.Closeable
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.minutes
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.connectcontroller.ConnectController
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  flags: RuntimeFlags,
  connectController: ConnectController?,
  kailleraServer: KailleraServer?,
  private val statsCollector: StatsCollector,
  releaseInfo: ReleaseInfo?
) : Closeable {
  private var publicInfo: PublicServerInformation? = null
  private var emulinkerMasterTask: EmuLinkerMasterUpdateTask? = null
  private var kailleraMasterTask: KailleraMasterUpdateTask? = null
  private val timer = Timer()

  override fun close() {
    timer.cancel()
  }

  fun run() {
    timer.schedule(delay = 1.minutes.inWholeMilliseconds, period = 1.minutes.inWholeMilliseconds) {
      logger.atFine().log("MasterListUpdater touching masters...")
      emulinkerMasterTask?.touchMaster()
      kailleraMasterTask?.touchMaster()
      statsCollector.clearStartedGamesList()
    }
  }

  init {
    if (flags.touchKaillera || flags.touchEmulinker) {
      publicInfo = PublicServerInformation(flags)
    }
    if (flags.touchKaillera) {
      kailleraMasterTask =
        KailleraMasterUpdateTask(
          publicInfo!!,
          connectController!!,
          kailleraServer!!,
          statsCollector,
          releaseInfo!!
        )
    }
    if (flags.touchEmulinker) {
      emulinkerMasterTask =
        EmuLinkerMasterUpdateTask(
          publicInfo!!,
          connectController!!,
          kailleraServer!!,
          releaseInfo!!
        )
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
