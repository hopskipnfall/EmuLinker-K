package org.emulinker.kaillera.master.client

import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.StatsCollector

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  private val statsCollector: StatsCollector,
  private val serverCheckinTask: ServerCheckinTask,
  private val emuLinkerMasterUpdateTask: EmuLinkerMasterUpdateTask,
  private val kailleraMasterUpdateTask: KailleraMasterUpdateTask,
  private val timer: Timer,
) {
  private var timerJob: TimerTask? = null

  @Synchronized
  fun stop() {
    timerJob?.cancel()
  }

  fun run() {
    timerJob =
      timer.schedule(
        // Give a few seconds to allow the server to bind ports etc.
        delay = 10.seconds.inWholeMilliseconds,
        period = REPORTING_INTERVAL.inWholeMilliseconds
      ) {
        runBlocking(Dispatchers.IO) {
          serverCheckinTask.touchMaster()
          if (flags.touchEmulinker) emuLinkerMasterUpdateTask.touchMaster()
          if (flags.touchKaillera) kailleraMasterUpdateTask.touchMaster()
        }
        statsCollector.clearStartedGamesList()
      }
  }

  private companion object {
    val REPORTING_INTERVAL = 1.minutes
  }
}
