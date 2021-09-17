package org.emulinker.kaillera.pico;

import dagger.Component;
import javax.inject.Singleton;
import org.apache.commons.configuration.Configuration;
import org.emulinker.kaillera.controller.KailleraServerController;
import org.emulinker.kaillera.controller.connectcontroller.ConnectController;
import org.emulinker.release.ReleaseInfo;

@Singleton
@Component(modules = AppModule.class)
public abstract class AppComponent {
  public abstract Configuration getConfiguration();

  public abstract ReleaseInfo getReleaseInfo();

  public abstract ConnectController getServer();

  public abstract KailleraServerController getKailleraServerController();
}
