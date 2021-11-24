package org.emulinker.kaillera.master;

import org.emulinker.config.RuntimeFlags;

public class PublicServerInformation {
  private final RuntimeFlags flags;

  public PublicServerInformation(RuntimeFlags flags) {
    this.flags = flags;
  }

  public String getServerName() {
    return flags.getServerName();
  }

  public String getLocation() {
    return flags.getServerLocation();
  }

  public String getWebsite() {
    return flags.getServerWebsite();
  }

  public String getConnectAddress() {
    return flags.getServerAddress();
  }
}
