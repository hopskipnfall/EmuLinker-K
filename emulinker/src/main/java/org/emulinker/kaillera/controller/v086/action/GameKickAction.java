package org.emulinker.kaillera.controller.v086.action;

import com.google.common.flogger.FluentLogger;
import org.emulinker.kaillera.controller.messaging.MessageFormatException;
import org.emulinker.kaillera.controller.v086.V086Controller;
import org.emulinker.kaillera.controller.v086.protocol.*;
import org.emulinker.kaillera.model.exception.GameKickException;

public class GameKickAction implements V086Action {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String desc = "GameKickAction";
  private static GameKickAction singleton = new GameKickAction();

  public static GameKickAction getInstance() {
    return singleton;
  }

  private int actionCount = 0;

  private GameKickAction() {}

  @Override
  public int getActionPerformedCount() {
    return actionCount;
  }

  @Override
  public String toString() {
    return desc;
  }

  @Override
  public void performAction(V086Message message, V086Controller.V086ClientHandler clientHandler)
      throws FatalActionException {
    actionCount++;

    GameKick kickRequest = (GameKick) message;

    try {
      clientHandler.getUser().gameKick(kickRequest.userId());
    } catch (GameKickException e) {
      logger.atSevere().withCause(e).log("Failed to kick");
      // new SF MOD - kick errors notifications
      try {
        clientHandler.send(
            GameChat_Notification.create(
                clientHandler.getNextMessageNumber(), "Error", e.getMessage()));
      } catch (MessageFormatException ex) {
        logger.atSevere().withCause(ex).log("Failed to contruct GameChat_Notification message");
      }
    }
  }
}
