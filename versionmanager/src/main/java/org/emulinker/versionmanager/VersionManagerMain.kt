package org.emulinker.versionmanager

import com.google.common.flogger.FluentLogger
import kotlinx.coroutines.runBlocking

private val logger = FluentLogger.forEnclosingClass()

/** Main entry point for the version manager/runner. */
fun main(): Unit = runBlocking {
  System.setProperty(
    "flogger.backend_factory",
    "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance"
  )

  logger.atSevere().log("Running!")

  // Read local file to see what version we are currently using and other state.

  // Read local config for settings on auto updates, api token, etc.

  // Call API to fetch latest detailed version info.

  // If necessary, download a new server binary.

  // Run server binary.
}
