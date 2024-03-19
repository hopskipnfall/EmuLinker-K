package org.emulinker.kaillera.controller.v086.action

import com.google.common.flogger.FluentLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.controller.v086.protocol.QuitGameNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitGameRequest
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.model.event.UserQuitGameEvent
import org.emulinker.kaillera.model.exception.CloseGameException
import org.emulinker.kaillera.model.exception.DropGameException
import org.emulinker.kaillera.model.exception.QuitGameException
import org.emulinker.util.EmuUtil.threadSleep

@Singleton
class QuitGameAction @Inject constructor(private val lookingForGameReporter: TwitterBroadcaster) :
  V086Action<QuitGameRequest>, V086GameEventHandler<UserQuitGameEvent> {
  override var actionPerformedCount = 0
    private set
  override var handledEventCount = 0
    private set

  override fun toString() = "QuitGameAction"

  @Throws(FatalActionException::class)
  override fun performAction(message: QuitGameRequest, clientHandler: V086ClientHandler) {
    actionPerformedCount++
    try {
      clientHandler.user.quitGame()
      lookingForGameReporter.cancelActionsForUser(clientHandler.user.id)
    } catch (e: DropGameException) {
      logger.atSevere().withCause(e).log("Action failed")
    } catch (e: QuitGameException) {
      logger.atSevere().withCause(e).log("Action failed")
    } catch (e: CloseGameException) {
      logger.atSevere().withCause(e).log("Action failed")
    }
    threadSleep(100.milliseconds)
  }

  override fun handleEvent(event: UserQuitGameEvent, clientHandler: V086ClientHandler) {
    handledEventCount++
    val thisUser = clientHandler.user
    try {
      val user = event.user
      if (!user.inStealthMode) {
        clientHandler.send(
          QuitGameNotification(clientHandler.nextMessageNumber, user.name!!, user.id)
        )
      }
      if (thisUser === user) {
        if (user.inStealthMode)
          clientHandler.send(
            QuitGameNotification(clientHandler.nextMessageNumber, user.name!!, user.id)
          )
      }
    } catch (e: MessageFormatException) {
      logger.atSevere().withCause(e).log("Failed to construct QuitGame.Notification message")
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
