package org.emulinker.kaillera.lookingforgame;

import dagger.Binds;
import dagger.Module;

@Module
public abstract class LookingForGameModule {
  @Binds
  public abstract LookingForGameReporter bindLookingForGameReporter(
      LookingForGameReporter reporter);
}
