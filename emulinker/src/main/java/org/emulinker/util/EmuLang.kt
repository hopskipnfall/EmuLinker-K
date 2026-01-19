package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

object EmuLang {
  private const val BUNDLE_NAME = "messages"

  private val DEFAULT_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT)

  // currentBundle will hold the language-specific bundle, strictly matching the requested locale
  // (no fallback to root).
  // It may be null if the requested language bundle does not exist.
  private var currentBundle: ResourceBundle? = null
  private var isCustomMode = false

  private val logger = FluentLogger.forEnclosingClass()

  fun updateLanguage(language: String) {
    if (language.equals("custom", ignoreCase = true)) {
      isCustomMode = true
      currentBundle = null
      logger.atInfo().log("EmuLang configured for Custom User Strings")
    } else {
      isCustomMode = false
      val locale = Locale(language)
      try {
        // Load strictly the specific language bundle, no fallback to standard default.
        // If messages_xx.properties doesn't exist, this throws MissingResourceException.
        currentBundle =
          ResourceBundle.getBundle(
            BUNDLE_NAME,
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
          )
        logger.atInfo().log("EmuLang found resource bundle for language: %s", language)
      } catch (_: MissingResourceException) {
        // This is expected if the language is not supported (e.g. 'uk' but no
        // messages_uk.properties).
        // We catch it and set currentBundle to null, so we skip to CustomUserStrings.
        logger
          .atInfo()
          .log(
            "No resource bundle found for language: %s. Will fallback to CustomUserStrings then Default.",
            language,
          )
        currentBundle = null
      }
    }
  }

  fun hasString(key: String): Boolean {
    if (isCustomMode) {
      if (CustomUserStrings.hasString(key)) {
        return true
      }
      return DEFAULT_BUNDLE.containsKey(key)
    } else {
      // Standard Mode: Language -> Custom -> Default
      val cb = currentBundle
      if (cb != null && cb.containsKey(key)) {
        return true
      }
      if (CustomUserStrings.hasString(key)) {
        return true
      }
      return DEFAULT_BUNDLE.containsKey(key)
    }
  }

  fun getStringOrNull(key: String): String? {
    if (isCustomMode) {
      if (CustomUserStrings.hasString(key)) {
        return CustomUserStrings.getString(key)
      }
      return try {
        DEFAULT_BUNDLE.getString(key)
      } catch (e: MissingResourceException) {
        logger.atWarning().withCause(e).log("Message key %s was not in custom bundle or default bundle.", key)
        null
      }
    } else {
      // Standard Mode: Language -> Custom -> Default
      // 1. Try Language Specific Bundle
      val cb = currentBundle
      if (cb != null) {
        try {
          return cb.getString(key)
        } catch (e: MissingResourceException) {
          logger.atFine().withCause(e).log("Key %s was not found in language bundle.", key)
          // Key missing in specific bundle, proceed to fallback
        }
      }

      // 2. Try Custom User Strings
      if (CustomUserStrings.hasString(key)) {
        return CustomUserStrings.getString(key)
      }

      // 3. Try Default Bundle (Root/English)
      return try {
        DEFAULT_BUNDLE.getString(key)
      } catch (e: MissingResourceException) {
        logger.atWarning().withCause(e).log("Key %s was not found in default bundle", key)
        null
      }
    }
  }

  fun getString(key: String): String {
    if (isCustomMode) {
      if (CustomUserStrings.hasString(key)) {
        return CustomUserStrings.getString(key)
      }
      return try {
        DEFAULT_BUNDLE.getString(key)
      } catch (e: MissingResourceException) {
        logger.atSevere().withCause(e).log("Missing language property: %s", key)
        key
      }
    } else {
      // Standard Mode: Language -> Custom -> Default
      // 1. Try Language Specific Bundle
      if (currentBundle != null) {
        try {
          return currentBundle!!.getString(key)
        } catch (e: MissingResourceException) {
          // Fallback
        }
      }

      // 2. Try Custom User Strings
      if (CustomUserStrings.hasString(key)) {
        return CustomUserStrings.getString(key)
      }

      // 3. Try Default Bundle
      return try {
        DEFAULT_BUNDLE.getString(key)
      } catch (e: MissingResourceException) {
        logger.atSevere().withCause(e).log("Missing language property: %s", key)
        key
      }
    }
  }

  fun getString(key: String, vararg messageArgs: Any?): String {
    val str = getString(key)
    // If the string was effectively the key (missing), don't format it to avoid exceptions if args
    // provided
    if (str == key && !hasString(key)) return str
    return try {
      MessageFormat(str).format(messageArgs)
    } catch (e: Exception) {
      logger.atSevere().withCause(e).log("Error formatting string: %s", key)
      str
    }
  }
}
