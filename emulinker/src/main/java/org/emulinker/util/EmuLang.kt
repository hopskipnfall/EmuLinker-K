package org.emulinker.util

import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.text.MessageFormat
import java.util.MissingResourceException
import java.util.ResourceBundle

object EmuLang {

  private const val BUNDLE_NAME = "language"

  private val RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME)

  private val logger = FluentLogger.forEnclosingClass()

  /*
  	public static void reload()
  	{
  		try
  		{
  			Class klass = RESOURCE_BUNDLE.getClass().getSuperclass();
  			Field field = klass.getDeclaredField("cacheList");
  			field.setAccessible(true);
  			sun.misc.SoftCache cache = (sun.misc.SoftCache)field.get(null);
  			cache.clear();
  		}
  		catch(Exception e)
  		{

  		}
  	}
  */

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

  fun getStringOrDefault(key: String, default: String): String {
    return try {
      RESOURCE_BUNDLE.getString(key)
    } catch (e: MissingResourceException) {
      default
    }
  }

  fun getString(key: String): String {
    return try {
      RESOURCE_BUNDLE.getString(key)
    } catch (e: MissingResourceException) {
      logger.atSevere().withCause(e).log("Missing language property: %s", key)
      key
    }
  }

  fun getString(key: String, vararg messageArgs: Any?): String {
    return try {
      val str = RESOURCE_BUNDLE.getString(key)
      MessageFormat(str).format(messageArgs)
    } catch (e: MissingResourceException) {
      logger.atSevere().withCause(e).log("Missing language property: %s", key)
      key
    }
  }
}
