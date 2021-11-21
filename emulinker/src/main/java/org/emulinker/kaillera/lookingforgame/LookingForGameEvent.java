package org.emulinker.kaillera.lookingforgame;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class LookingForGameEvent {
  public abstract int userId();

  public abstract int gameId();

  public abstract String username();

  public abstract String gameTitle();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setUserId(int userId);

    public abstract Builder setGameId(int gameId);

    public abstract Builder setUsername(String username);

    public abstract Builder setGameTitle(String gameTitle);

    public abstract LookingForGameEvent build();
  }

  public static Builder builder() {
    return new AutoValue_LookingForGameEvent.Builder();
  }
}
