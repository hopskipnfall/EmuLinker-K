package org.emulinker.kaillera.lookingforgame;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class LookingForGameEvent {
    public abstract int userId();

    public abstract int gameId();

    @AutoValue.Builder
    public static abstract class Builder {
        public abstract Builder setUserId(int userId);

        public abstract Builder setGameId(int gameId);

        public abstract LookingForGameEvent build();
    }
}
