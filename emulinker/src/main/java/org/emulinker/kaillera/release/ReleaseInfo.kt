package org.emulinker.kaillera.release

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import org.emulinker.kaillera.pico.CompiledFlags.BUILD_DATE
import org.emulinker.kaillera.pico.CompiledFlags.PROJECT_NAME
import org.emulinker.kaillera.pico.CompiledFlags.PROJECT_URL
import org.emulinker.kaillera.pico.CompiledFlags.PROJECT_VERSION
import org.emulinker.util.EmuUtil

/**
 * Provides release and build information for the EmuLinker project. This class also formats a
 * welcome message for printing at server startup.
 */
@Singleton
class ReleaseInfo @Inject constructor() {
  val productName: String = PROJECT_NAME

  val versionString: String = PROJECT_VERSION

  val shortVersionString: String = "ESFN$versionString"

  val buildDate: Instant = BUILD_DATE

  val websiteString: String = PROJECT_URL

  val licenseInfo = "Usage of this sofware is subject to the terms found in the included license"

  /**
   * Formats release information into a welcome message. This message is printed by the server at
   * server startup.
   */
  val welcome =
    """// $productName version $versionString (${EmuUtil.toSimpleUtcDatetime(buildDate)}) 
// $licenseInfo
// For the most up-to-date information please visit: $websiteString"""
}
