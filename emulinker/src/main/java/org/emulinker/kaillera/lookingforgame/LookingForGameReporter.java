package org.emulinker.kaillera.lookingforgame;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Observes when a user is looking for a game opponent and publishes a report to one or more
 * external services (e.g. Twitter, Discord).
 */
@Singleton
public final class LookingForGameReporter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration TIME_BEFORE_REPORTING = Duration.ofSeconds(10);

  private final Map<Integer, LookingForGameReport> reports = new HashMap<>();

  @Inject
  LookingForGameReporter() {}

  /** After the number of seconds defined in the config, it will report. */
  public void reportLookingForGame() {}

  public void cancelLookingForGame() {}

  @AutoValue
  public abstract static class LookingForGameReport {
    public abstract String message();

    /** ID of the user looking for opponents. */
    public abstract int userId();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMessage(String message);

      public abstract Builder setUserId(int userId);

      public abstract LookingForGameReport build();
    }
  }
}
