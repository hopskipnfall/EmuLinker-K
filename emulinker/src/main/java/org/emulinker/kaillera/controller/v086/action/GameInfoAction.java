package org.emulinker.kaillera.controller.v086.action;

import com.google.common.flogger.FluentLogger;
import org.emulinker.kaillera.controller.messaging.MessageFormatException;
import org.emulinker.kaillera.controller.v086.V086Controller;
import org.emulinker.kaillera.controller.v086.protocol.GameChat_Notification;
import org.emulinker.kaillera.model.event.*;

public class GameInfoAction implements V086GameEventHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String desc = "GameInfoAction";
  private static GameInfoAction singleton = new GameInfoAction();

  public static GameInfoAction getInstance() {
    return singleton;
  }

  private int handledCount = 0;

  private GameInfoAction() {}

  @Override
  public int getHandledEventCount() {
    return handledCount;
  }

  @Override
  public String toString() {
    return desc;
  }

  @Override
  public void handleEvent(GameEvent event, V086Controller.V086ClientHandler clientHandler) {
    handledCount++;

    GameInfoEvent infoEvent = (GameInfoEvent) event;

    if (infoEvent.getUser() != null) {
      if (infoEvent.getUser() != clientHandler.getUser()) return;
    }

    try {
      clientHandler.send(
          GameChat_Notification.create(
              clientHandler.getNextMessageNumber(), "Server", infoEvent.getMessage()));
    } catch (MessageFormatException e) {
      logger.atSevere().withCause(e).log("Failed to contruct GameChat_Notification message");
    }
  }
}
