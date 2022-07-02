package org.emulinker.kaillera.pico

import com.google.common.truth.Truth.assertThat
import io.ktor.network.sockets.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.emulinker.eval.client.EvalClient
import org.emulinker.kaillera.model.GameStatus
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
      delay(3.seconds)

      // Stop the server.
      component.kailleraServerController.stop()
      component.server.stop()
      delay(1.seconds)

      // Make sure that the coroutines for those tasks were successful.
      assertThat(kailleraServerControllerTask.isCompleted).isTrue()
      assertThat(serverTask.isCompleted).isTrue()
    }
  }

  @Test
  fun createGame() {
    runBlocking {
      val component = DaggerAppComponent.create()

      val server = component.server

      val kailleraServerControllerTask =
          launch { component.kailleraServerController.start() } // Apparently cannot be removed.
      val serverTask = launch { server.start() }

      delay(1.seconds)

      val user1 = EvalClient("testuser1", InetSocketAddress("127.0.0.1", 27888))

      user1.connectToDedicatedPort()
      user1.start()

      delay(1.seconds)

      val controller = server.controllers.first()
      assertThat(controller.clientHandlers).hasSize(1)
      val clientHandler = controller.clientHandlers.values.first()

      user1.createGame()
      delay(100.milliseconds)

      assertThat(clientHandler.user.game).isNotNull()
      assertThat(clientHandler.user.game!!.status == GameStatus.WAITING)

      user1.quitGame()
      user1.quitServer()

      delay(1.seconds)

      assertThat(controller.clientHandlers).isEmpty()

      // Clean up.
      user1.close()
      component.kailleraServerController.stop()
      component.server.stop()
      delay(1.seconds)

      // Make sure that the coroutines for those tasks were successful.
      assertThat(kailleraServerControllerTask.isCompleted).isTrue()
      assertThat(serverTask.isCompleted).isTrue()
    }
  }
}
