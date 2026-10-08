package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import org.apache.commons.configuration.PropertiesConfiguration
import org.junit.Test

class EmuLinkerPropertiesConfigTest {
  @Test
  fun splitListMatchesTheOldCommonsConfigurationRules() {
    assertThat(EmuLinkerPropertiesConfig.splitList("1, 2 ,3")).containsExactly("1", "2", "3")
    assertThat(EmuLinkerPropertiesConfig.splitList("a\\,b, c")).containsExactly("a,b", "c")
    assertThat(EmuLinkerPropertiesConfig.splitList("1,,2")).containsExactly("1", "", "2")
    assertThat(EmuLinkerPropertiesConfig.splitList("single")).containsExactly("single")
    assertThat(EmuLinkerPropertiesConfig.splitList("")).isEmpty()
  }

  @Test
  fun delimiterParsingOffKeepsCommasInPlainValues() {
    val config = PropertiesConfiguration()
    config.isDelimiterParsingDisabled = true
    config.load(StringReader("masterList.serverLocation=Tokyo, Japan"))

    assertThat(config.getString("masterList.serverLocation")).isEqualTo("Tokyo, Japan")
  }

  @Test
  fun shippedConfigLoadsAndUsesTheListAccessors() {
    val config = EmuLinkerPropertiesConfig()

    assertThat(config.getString("masterList.serverName")).isEqualTo("Local Dev Server")
    assertThat(config.getInt("server.maxPing")).isGreaterThan(0)
    // A missing list key is an empty list, as before.
    assertThat(config.getStringArray("this.key.does.not.exist")).isEmpty()
    // Typical list keys in the release configuration.
    assertThat(EmuLinkerPropertiesConfig.splitList("1,2,3,4,5,6")).hasSize(6)
  }
}
