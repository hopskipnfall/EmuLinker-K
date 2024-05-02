package org.emulinker.kaillera.master.client

import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.util.TaskScheduler

@Singleton
class MasterListUpdater
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  private val statsCollector: StatsCollector,
  private val serverCheckinTask: ServerCheckinTask,
  private val emuLinkerMasterUpdateTask: EmuLinkerMasterUpdateTask,
  private val kailleraMasterUpdateTask: KailleraMasterUpdateTask,
  private val taskScheduler: TaskScheduler,
) {
  private var timerJob: TimerTask? = null

  fun stop() {
    timerJob?.cancel()
    timerJob = null
  }

  fun run() {
    timerJob =
      taskScheduler.scheduleRepeating(
        // Give a few seconds to allow the server to bind ports etc.
        initialDelay = 10.seconds,
        period = REPORTING_INTERVAL
      ) {
        serverCheckinTask.reportStatus()
        if (flags.touchEmulinker) emuLinkerMasterUpdateTask.reportStatus()
        if (flags.touchKaillera) kailleraMasterUpdateTask.reportStatus()
        statsCollector.clearStartedGamesList()
      }
  }

  private companion object {
    val REPORTING_INTERVAL = 1.minutes
  }
}
