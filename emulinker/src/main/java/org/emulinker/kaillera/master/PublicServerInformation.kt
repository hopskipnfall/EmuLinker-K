package org.emulinker.kaillera.master

import javax.inject.Inject
import org.emulinker.config.RuntimeFlags

class PublicServerInformation @Inject constructor(flags: RuntimeFlags) {
  val serverName: String = flags.serverName
  val location: String = flags.serverLocation
  val website: String = flags.serverWebsite
  val connectAddress: String = flags.serverAddress
}
