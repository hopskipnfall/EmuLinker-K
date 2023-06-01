package org.emulinker.kaillera.master.client

import com.google.common.flogger.FluentLogger
import java.util.Timer
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.util.Executable

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  private val threadPool: ThreadPoolExecutor,
  private val statsCollector: StatsCollector,
  private val serverCheckinTask: ServerCheckinTask,
  private val emuLinkerMasterUpdateTask: EmuLinkerMasterUpdateTask,
  private val kailleraMasterUpdateTask: KailleraMasterUpdateTask,
) : Executable {
  private var stopFlag = false

  @get:Synchronized
  override var threadIsActive = false
    private set

  private val timer = Timer()

  @Synchronized
  fun start() {
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

  @Synchronized
  override fun stop() {
    logger.atFine().log("MasterListUpdater thread received stop request!")
    if (!threadIsActive) {
      logger.atFine().log("MasterListUpdater thread stop request ignored: not running!")
      return
    }
    timer.cancel()
    stopFlag = true
  }

  override fun run() {
    threadIsActive = true
    timer.schedule(
      // Give a few seconds to allow the server to bind ports etc.
      delay = 10.seconds.inWholeMilliseconds,
      period = REPORTING_INTERVAL.inWholeMilliseconds
    ) {
      logger.atInfo().log("MasterListUpdater touching masters...")
      serverCheckinTask.touchMaster()
      if (flags.touchEmulinker) emuLinkerMasterUpdateTask.touchMaster()
      if (flags.touchKaillera) kailleraMasterUpdateTask.touchMaster()

      statsCollector.clearStartedGamesList()
    }
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    val REPORTING_INTERVAL = 1.minutes
  }
}
