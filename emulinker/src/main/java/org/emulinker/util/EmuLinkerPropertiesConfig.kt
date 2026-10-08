package org.emulinker.util

import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.configuration.PropertyConverter

/**
 * Reads `emulinker.cfg` from the classpath.
 *
 * Commons Configuration splits every value on commas by default, which silently truncated plain
 * values such as `masterList.serverLocation=Tokyo, Japan` to `Tokyo`. List splitting is therefore
 * turned off and done only where a list is requested ([getStringArray]), with the same rules as
 * before: elements are separated by commas, whitespace around them is trimmed, and `\,` is a
 * literal comma.
 */
open class EmuLinkerPropertiesConfig : PropertiesConfiguration() {
  init {
    isDelimiterParsingDisabled = true
    loadConfiguration()
    isThrowExceptionOnMissing = true
  }

  /** Loads the configuration. Overridable only so tests can supply their own text. */
  protected open fun loadConfiguration() {
    val url =
      requireNotNull(EmuLinkerPropertiesConfig::class.java.getResource("/emulinker.cfg")) {
        "emulinker.cfg was not found on the classpath"
      }
    load(url)
  }

  override fun getString(key: String): String = unescapeCommas(super.getString(key))

  override fun getString(key: String, defaultValue: String?): String? =
    super.getString(key, defaultValue)?.let(::unescapeCommas)

  override fun getStringArray(key: String): Array<String> =
    super.getStringArray(key).flatMap { splitList(it) }.toTypedArray()

  // Numbers and booleans used to be parsed from the first element of a comma separated value, so a
  // config with a stray trailing comma (`server.maxUsers=100,`) kept working. Keep accepting that.

  override fun getInt(key: String): Int =
    firstElement(key)?.let { PropertyConverter.toInteger(it).toInt() } ?: throw missing(key)

  override fun getInt(key: String, defaultValue: Int): Int =
    firstElement(key)?.let { PropertyConverter.toInteger(it).toInt() } ?: defaultValue

  override fun getInteger(key: String, defaultValue: Int?): Int? =
    firstElement(key)?.let { PropertyConverter.toInteger(it).toInt() } ?: defaultValue

  override fun getBoolean(key: String): Boolean =
    firstElement(key)?.let { PropertyConverter.toBoolean(it) } ?: throw missing(key)

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    firstElement(key)?.let { PropertyConverter.toBoolean(it) } ?: defaultValue

  /** The first comma separated element of [key]'s value, or null if the key is not set. */
  private fun firstElement(key: String): String? =
    super.getString(key, null)?.let { splitList(it).firstOrNull() ?: "" }

  private fun missing(key: String) =
    NoSuchElementException("'$key' doesn't map to an existing object")

  companion object {
    private val UNESCAPED_COMMA = Regex("(?<!\\\\),")

    private fun unescapeCommas(value: String) = value.replace("\\,", ",")

    /** Splits a list value on unescaped commas. An empty value is an empty list. */
    internal fun splitList(value: String): List<String> =
      if (value.isEmpty()) emptyList()
      else value.split(UNESCAPED_COMMA).map { unescapeCommas(it.trim()) }
  }
}
