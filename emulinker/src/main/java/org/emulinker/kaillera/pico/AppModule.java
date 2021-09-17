package org.emulinker.kaillera.pico;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.emulinker.kaillera.access.AccessManager;
import org.emulinker.kaillera.access.AccessManager2;
import org.emulinker.kaillera.controller.KailleraServerController;
import org.emulinker.kaillera.controller.v086.V086Controller;
import org.emulinker.kaillera.master.MasterListStatsCollector;
import org.emulinker.kaillera.master.StatsCollector;
import org.emulinker.kaillera.model.KailleraServer;
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory;
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactoryImpl;
import org.emulinker.kaillera.model.impl.KailleraServerImpl;
import org.emulinker.kaillera.release.KailleraServerReleaseInfo;
import org.emulinker.release.ReleaseInfo;
import org.emulinker.util.EmuLinkerExecutor;
import org.emulinker.util.EmuLinkerPropertiesConfig;

@Module
public abstract class AppModule {

  @Provides
  public static Configuration provideConfiguration() {
    try {
      return new EmuLinkerPropertiesConfig();
    } catch (ConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Provides
  public static ThreadPoolExecutor providThreadPoolExecutor(EmuLinkerExecutor emuLinkerExecutor) {
    return emuLinkerExecutor;
  }

  @Provides
  public static ReleaseInfo provideKailleraServerReleaseInfo(
      KailleraServerReleaseInfo kailleraServerReleaseInfo) {
    return kailleraServerReleaseInfo;
  }

  @Provides
  public static AccessManager provideAccessManager(AccessManager2 accessManager2) {
    return accessManager2;
  }

  @Provides
  public static AutoFireDetectorFactory provideAutoFireDetectorFactory(
      AutoFireDetectorFactoryImpl autoFireDetectorFactoryImpl) {
    return autoFireDetectorFactoryImpl;
  }

  @Provides
  public static KailleraServer provideKailleraServer(KailleraServerImpl kailleraServerImpl) {
    return kailleraServerImpl;
  }

  @Provides
  public static KailleraServerController provideKailleraServerController(
      V086Controller v086Controller) {
    return v086Controller;
  }

  @Provides
  public static StatsCollector providStatsCollector(
      MasterListStatsCollector masterListStatsCollector) {
    return masterListStatsCollector;
  }

  @Binds
  @IntoSet
  public abstract KailleraServerController bindKailleraServerController(
      V086Controller v086Controller);
}
