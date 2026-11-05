package org.emulinker.util

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.StringReader
import org.apache.commons.configuration.PropertiesConfiguration

class EmuLinkerPropertiesConfigTest :
  FunSpec({
    test("splitList matches the old Commons Configuration rules") {
      EmuLinkerPropertiesConfig.splitList("1, 2 ,3").shouldContainExactly("1", "2", "3")
      EmuLinkerPropertiesConfig.splitList("a\\,b, c").shouldContainExactly("a,b", "c")
      EmuLinkerPropertiesConfig.splitList("1,,2").shouldContainExactly("1", "", "2")
      EmuLinkerPropertiesConfig.splitList("single").shouldContainExactly("single")
      EmuLinkerPropertiesConfig.splitList("").shouldBeEmpty()
    }

    test("delimiterParsingOff keeps commas in plain values") {
      val config = PropertiesConfiguration()
      config.isDelimiterParsingDisabled = true
      config.load(StringReader("masterList.serverLocation=Tokyo, Japan"))

      config.getString("masterList.serverLocation") shouldBe "Tokyo, Japan"
    }

    test("shipped config loads and uses the list accessors") {
      val config = EmuLinkerPropertiesConfig()

      config.getString("masterList.serverName") shouldBe "Local Dev Server"
      config.getInt("server.maxPing") shouldBeGreaterThan 0
      // A missing list key is an empty list, as before.
      config.getStringArray("this.key.does.not.exist").shouldBeEmpty()
      // Typical list keys in the release configuration.
      EmuLinkerPropertiesConfig.splitList("1,2,3,4,5,6") shouldHaveSize 6
    }

    test("numbers and booleans still accept a trailing comma") {
      val config =
        object : EmuLinkerPropertiesConfig() {
          override fun loadConfiguration() {
            load(StringReader("a=100,\nb=true,\nc=7, 8\nd=Tokyo, Japan\ne=\n"))
          }
        }

      assertSoftly {
        config.getInt("a") shouldBe 100
        config.getInt("a", 5) shouldBe 100
        config.getInt("c") shouldBe 7
        config.getBoolean("b").shouldBeTrue()
        config.getBoolean("b", false).shouldBeTrue()
        config.getInt("missing", 42) shouldBe 42
        config.getString("d") shouldBe "Tokyo, Japan"
        config.getStringArray("c").toList().shouldContainExactly("7", "8")
        config.getStringArray("e").shouldBeEmpty()
      }
      shouldThrow<NoSuchElementException> { config.getInt("missing") }
    }
  })
