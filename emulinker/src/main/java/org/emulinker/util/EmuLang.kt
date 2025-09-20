package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.text.MessageFormat
import java.util.MissingResourceException
import java.util.ResourceBundle

object EmuLang {
  private const val BUNDLE_NAME = "messages"

  private val RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME)

  private val logger = FluentLogger.forEnclosingClass()

  fun hasString(key: String): Boolean {
    if (RESOURCE_BUNDLE.containsKey(key)) {
      try {
        RESOURCE_BUNDLE.getString(key)
        return true
      } catch (e: Exception) {
        // It exists but is not readable.
        e.printStackTrace()
      }
    }
    return false
  }

  fun getStringOrNull(key: String): String? =
    try {
      RESOURCE_BUNDLE.getString(key)
    } catch (e: MissingResourceException) {
      null
    }

  fun getString(key: String): String =
    try {
      RESOURCE_BUNDLE.getString(key)
    } catch (e: MissingResourceException) {
      logger.atSevere().withCause(e).log("Missing language property: %s", key)
      key
    }

  fun getString(key: String, vararg messageArgs: Any?): String =
    try {
      val str = RESOURCE_BUNDLE.getString(key)
      MessageFormat(str).format(messageArgs)
    } catch (e: MissingResourceException) {
      logger.atSevere().withCause(e).log("Missing language property: %s", key)
      key
    }
}
