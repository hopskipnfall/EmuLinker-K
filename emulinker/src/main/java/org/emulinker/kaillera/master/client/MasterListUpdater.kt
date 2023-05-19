package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.controller.connectcontroller.ConnectController
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.EmuUtil.threadSleep
import org.emulinker.util.Executable

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  private val threadPool: ThreadPoolExecutor,
  connectController: ConnectController?,
  kailleraServer: KailleraServer?,
  private val statsCollector: StatsCollector,
  releaseInfo: ReleaseInfo?
) : Executable {
  private var publicInfo: PublicServerInformation? = null
  private var emulinkerMasterTask: EmuLinkerMasterUpdateTask? = null
  private var kailleraMasterTask: KailleraMasterUpdateTask? = null
  private var stopFlag = false

  @get:Synchronized
  override var threadIsActive = false
    private set

  @Synchronized
  override fun toString(): String {
    return ("MasterListUpdater[touchKaillera=" +
      flags.touchKaillera +
      " touchEmulinker=" +
      flags.touchEmulinker +
      "]")
  }

  @Synchronized
  fun start() {
    if (publicInfo != null) {
      logger.atFine().log("MasterListUpdater thread received start request!")
      logger
        .atFine()
        .log(
          "MasterListUpdater thread starting (ThreadPool:%d/%d)",
          threadPool.activeCount,
          threadPool.poolSize
        )
      threadPool.execute(this)
      Thread.yield()
      logger
        .atFine()
        .log(
          "MasterListUpdater thread started (ThreadPool:%d/%d)",
          threadPool.activeCount,
          threadPool.poolSize
        )
    }
  }

  @Synchronized
  override fun stop() {
    if (publicInfo != null) {
      logger.atFine().log("MasterListUpdater thread received stop request!")
      if (!threadIsActive) {
        logger.atFine().log("MasterListUpdater thread stop request ignored: not running!")
        return
      }
      stopFlag = true
    }
  }

  override fun run() {
    threadIsActive = true
    logger.atFine().log("MasterListUpdater thread running...")
    try {
      while (!stopFlag) {
        threadSleep(1.minutes)
        if (stopFlag) break
        logger.atInfo().log("MasterListUpdater touching masters...")
        if (emulinkerMasterTask != null) emulinkerMasterTask!!.touchMaster()
        if (kailleraMasterTask != null) kailleraMasterTask!!.touchMaster()
        statsCollector.clearStartedGamesList()
      }
    } finally {
      threadIsActive = false
      logger.atFine().log("MasterListUpdater thread exiting...")
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
