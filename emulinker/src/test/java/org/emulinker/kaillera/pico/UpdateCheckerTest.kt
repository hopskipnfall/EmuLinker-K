package org.emulinker.kaillera.pico

import java.io.File
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import org.junit.Test

class UpdateCheckerTest {

  @Test
  fun validateUpdateMessagesConfig() {
    val a = File("./conf/update_messages.json")

    Json.decodeFromString<VersionUpdatePromptConfig>(a.readText())
  }

  @Test
  fun assertBadMessageThrowsException() {
    assertFailsWith<Exception> {
      Json.decodeFromString<VersionUpdatePromptConfig>("{\"hello\": \"world\"}")
    }
  }
}
