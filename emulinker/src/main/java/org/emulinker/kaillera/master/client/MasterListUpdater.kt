package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.connectcontroller.ConnectController
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.Executable

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  connectController: ConnectController?,
  kailleraServer: KailleraServer?,
  private val statsCollector: StatsCollector,
  releaseInfo: ReleaseInfo?
) : Executable {
  private var publicInfo: PublicServerInformation? = null
  private var emulinkerMasterTask: EmuLinkerMasterUpdateTask? = null
  private var kailleraMasterTask: KailleraMasterUpdateTask? = null
  private var stopFlag = false
  private val timer = Timer()

  @get:Synchronized
  override var threadIsActive = false
    private set

  @Synchronized
  override fun toString() =
    "MasterListUpdater[touchKaillera=${flags.touchKaillera} touchEmulinker=${flags.touchEmulinker}]"

  @Synchronized
  fun start() {
    if (publicInfo != null) {
      logger.atFine().log("MasterListUpdater thread received start request!")
      //      threadPool.execute(this) // NUEFIXME
      //      Thread.yield() // nue removed
    }
  }

  override suspend fun stop() {
    if (publicInfo != null) {
      logger.atFine().log("MasterListUpdater thread received stop request!")
      timer.cancel()
      stopFlag = true
    }
  }

  override suspend fun run(globalContext: CoroutineContext) {
    threadIsActive = true
    timer.schedule(delay = 1.minutes.inWholeMilliseconds, period = 1.minutes.inWholeMilliseconds) {
      logger.atInfo().log("MasterListUpdater touching masters...")
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
