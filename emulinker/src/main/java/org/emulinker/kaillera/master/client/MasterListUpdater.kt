package org.emulinker.kaillera.master.client

import java.util.TimerTask
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.util.TaskScheduler

class MasterListUpdater(
  private val flags: RuntimeFlags,
  private val statsCollector: StatsCollector,
  private val serverCheckinTask: ServerCheckinTask,
  private val emuLinkerMasterUpdateTask: EmuLinkerMasterUpdateTask,
  private val kailleraMasterUpdateTask: KailleraMasterUpdateTask,
  private val taskScheduler: TaskScheduler,
) {
  private var listReporterJob: TimerTask? = null
  private var serverCheckinJob: TimerTask? = null

  fun stop() {
    listReporterJob?.cancel()
    listReporterJob = null

    serverCheckinJob?.cancel()
    serverCheckinJob = null
  }

  fun run() {
    if (flags.touchEmulinker || flags.touchKaillera) {
      listReporterJob =
        taskScheduler.scheduleRepeating(
          // Give a few seconds to allow the server to bind ports etc.
          initialDelay = 10.seconds,
          period = LIST_REPORTING_INTERVAL
        ) {
          if (flags.touchEmulinker) emuLinkerMasterUpdateTask.reportStatus()
          if (flags.touchKaillera) kailleraMasterUpdateTask.reportStatus()
          statsCollector.clearStartedGamesList()
        }
    }
    serverCheckinJob =
      taskScheduler.scheduleRepeating(initialDelay = 10.seconds, period = CHECKIN_INTERVAL) {
        serverCheckinTask.reportStatus()
      }
  }

  private companion object {
    val LIST_REPORTING_INTERVAL = 1.minutes
    val CHECKIN_INTERVAL = 30.minutes
  }
}
