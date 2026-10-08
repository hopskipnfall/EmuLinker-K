package org.emulinker.util

import org.apache.commons.configuration.PropertiesConfiguration

/**
 * Reads `emulinker.cfg` from the classpath.
 *
 * Commons Configuration splits every value on commas by default, which silently truncated plain
 * values such as `masterList.serverLocation=Tokyo, Japan` to `Tokyo`. List splitting is therefore
 * turned off and done only where a list is requested ([getStringArray]), with the same rules as
 * before: elements are separated by commas, whitespace around them is trimmed, and `\,` is a
 * literal comma.
 */
class EmuLinkerPropertiesConfig : PropertiesConfiguration() {
  init {
    isDelimiterParsingDisabled = true
    val url =
      requireNotNull(EmuLinkerPropertiesConfig::class.java.getResource("/emulinker.cfg")) {
        "emulinker.cfg was not found on the classpath"
      }
    load(url)
    isThrowExceptionOnMissing = true
  }

  override fun getString(key: String): String = unescapeCommas(super.getString(key))

  override fun getString(key: String, defaultValue: String?): String? =
    super.getString(key, defaultValue)?.let(::unescapeCommas)

  override fun getStringArray(key: String): Array<String> =
    super.getStringArray(key).flatMap { splitList(it) }.toTypedArray()

  companion object {
    private val UNESCAPED_COMMA = Regex("(?<!\\\\),")

    private fun unescapeCommas(value: String) = value.replace("\\,", ",")

    /** Splits a list value on unescaped commas. An empty value is an empty list. */
    internal fun splitList(value: String): List<String> =
      if (value.isEmpty()) emptyList()
      else value.split(UNESCAPED_COMMA).map { unescapeCommas(it.trim()) }
  }
}
