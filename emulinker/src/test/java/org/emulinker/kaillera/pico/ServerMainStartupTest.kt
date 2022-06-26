package org.emulinker.kaillera.pico

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ServerMainStartupTest {

  @Test
  fun startup() {
    runBlocking {
      val component = DaggerAppComponent.create()

      val kailleraServerControllerTask =
          launch { component.kailleraServerController.start() } // Apparently cannot be removed.
      val serverTask = launch { component.server.start() }

      // Make sure it stays alive for 10 seconds.
      delay(10.seconds)

      // Stop the server.
      component.kailleraServerController.stop()
      component.server.stop()
      delay(1.seconds)

      // Make sure that the coroutines for those tasks were successful.
      assertThat(kailleraServerControllerTask.isCompleted).isTrue()
      assertThat(serverTask.isCompleted).isTrue()
    }
  }
}
