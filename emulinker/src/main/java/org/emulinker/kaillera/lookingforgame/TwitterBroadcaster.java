package org.emulinker.kaillera.lookingforgame;

import com.google.common.flogger.FluentLogger;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.emulinker.config.RuntimeFlags;

/**
 * Observes when a user is looking for a game opponent and publishes a report to one or more
 * external services (e.g. Twitter, Discord).
 */
@Singleton
public final class TwitterBroadcaster {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RuntimeFlags flags;

  private static final ConcurrentMap<LookingForGameEvent, Disposable> pendingReports =
      new ConcurrentHashMap<>();

  @Inject
  TwitterBroadcaster(RuntimeFlags flags) {
    this.flags = flags;
  }

  /** After the number of seconds defined in the config, it will report. */
  public void reportAndStartTimer(LookingForGameEvent lookingForGameEvent) {
    Disposable disposable =
        Completable.timer(
                flags.twitterBroadcastDelay().getSeconds(), TimeUnit.SECONDS, Schedulers.io())
            .subscribe(
                () -> {
                  logger.atSevere().log("I have waited long enough.");
                  pendingReports.remove(lookingForGameEvent);
                });
    pendingReports.put(lookingForGameEvent, disposable);
  }

  public boolean cancelActionsForUser(int userId) {
    return cancelMatchingEvents((event) -> event.userId() == userId);
  }

  public boolean cancelActionsForGame(int gameId) {
    return cancelMatchingEvents((event) -> event.gameId() == gameId);
  }

  private boolean cancelMatchingEvents(Predicate<? super LookingForGameEvent> predicate) {
    return pendingReports.keySet().stream()
        .filter(predicate)
        // Use map instead of foreach because it lets us return whether or not something was
        // modified.
        .map(
            (event) -> {
              Disposable disposable = pendingReports.get(event);
              if (disposable != null) {
                logger.atSevere().log("Clearing outstanding request.");
                disposable.dispose();
                pendingReports.remove(event);
              }
              return event;
            })
        .findAny()
        .isPresent();
  }
}
