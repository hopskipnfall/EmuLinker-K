package org.emulinker.kaillera.release;

import javax.inject.Inject;
import org.emulinker.release.ReleaseInfo;

/**
 * Provides release and build information for the EmuLinker project. This class also formats a
 * welcome message for printing at server startup.
 */
public final class KailleraServerReleaseInfo implements ReleaseInfo {
  private final String productName = "EmuLinkerSF-Netosuma (Beta)";

  private final int majorVersion = 0;
  private final int minorVersion = 1;
  private final int buildNumber = 3;

  private final String releaseDate = "2021-09-16";
  private final String licenseInfo =
      "Usage of this sofware is subject to the terms found in the included license";
  private final String website = "https://github.com/hopskipnfall/EmuLinkerSF-Netosuma";

  @Inject
  KailleraServerReleaseInfo() {}

  @Override
  public final String getProductName() {
    return productName;
  }

  @Override
  public final int getMajorVersion() {
    return majorVersion;
  }

  @Override
  public final int getMinorVersion() {
    return minorVersion;
  }

  @Override
  public final String getReleaseDate() {
    return releaseDate;
  }

  @Override
  public final String getLicenseInfo() {
    return licenseInfo;
  }

  @Override
  public final String getWebsiteString() {
    return website;
  }

  @Override
  public final int getBuildNumber() {
    // TODO: modify this to pull from an Ant build version file
    return buildNumber;
  }

  /**
   * Returns the version number for the EmuLinker server in the form
   *
   * <p><i>major</i>.<i>minor</i>
   */
  @Override
  public final String getVersionString() {
    StringBuilder sb = new StringBuilder();
    // sb.append(getMajorVersion());
    // sb.append(".");
    sb.append(getMinorVersion());
    sb.append(".");
    sb.append(getBuildNumber());
    return sb.toString();
  }

  /**
   * Formats release information into a welcome message. This message is printed by the server at
   * server startup.
   */
  @Override
  public String getWelcome() {
    return String.format(
        "// %s version %s (%s) \n// %s\n// For the most up-to-date information please visit: %s",
        getProductName(),
        getVersionString(),
        getReleaseDate(),
        getLicenseInfo(),
        getWebsiteString());
  }
}
