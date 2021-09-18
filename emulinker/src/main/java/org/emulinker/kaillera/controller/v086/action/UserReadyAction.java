package org.emulinker.kaillera.controller.v086.action;

import com.google.common.flogger.FluentLogger;
import org.emulinker.kaillera.controller.messaging.MessageFormatException;
import org.emulinker.kaillera.controller.v086.V086Controller;
import org.emulinker.kaillera.controller.v086.protocol.*;
import org.emulinker.kaillera.model.event.*;
import org.emulinker.kaillera.model.exception.UserReadyException;

public class UserReadyAction implements V086Action, V086GameEventHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String desc = "UserReadyAction";
  private static UserReadyAction singleton = new UserReadyAction();

  public static UserReadyAction getInstance() {
    return singleton;
  }

  private int actionCount = 0;
  private int handledCount = 0;

  private UserReadyAction() {}

  @Override
  public int getActionPerformedCount() {
    return actionCount;
  }

  @Override
  public int getHandledEventCount() {
    return handledCount;
  }

  @Override
  public String toString() {
    return desc;
  }

  @Override
  public void performAction(V086Message message, V086Controller.V086ClientHandler clientHandler)
      throws FatalActionException {
    actionCount++;

    try {
      clientHandler.getUser().playerReady();
    } catch (UserReadyException e) {
      logger.atFine().withCause(e).log("Ready signal failed");
    }
  }

  @Override
  public void handleEvent(GameEvent event, V086Controller.V086ClientHandler clientHandler) {
    handledCount++;

    clientHandler.resetGameDataCache();

    try {
      clientHandler.send(AllReady.create(clientHandler.getNextMessageNumber()));
    } catch (MessageFormatException e) {
      logger.atSevere().withCause(e).log("Failed to contruct AllReady message");
    }
  }
}
