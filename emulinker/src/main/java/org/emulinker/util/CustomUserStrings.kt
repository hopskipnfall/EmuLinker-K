package org.emulinker.util

import java.text.MessageFormat
import java.util.ResourceBundle

object CustomUserStrings {
  private const val BUNDLE_NAME = "language"

  private val RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME)

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

  fun getString(key: String): String = RESOURCE_BUNDLE.getString(key)

  fun getString(key: String, vararg messageArgs: Any): String {
    val str = RESOURCE_BUNDLE.getString(key)
    return MessageFormat(str).format(messageArgs)
  }
}
