package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class EmuLangTest {

  @Test
  fun `default language behavior`() {
    // "en" or any standard language
    EmuLang.updateLanguage("en")

    // 1. Should find key in messages.properties
    assertWithMessage("String from messages.properties should be found")
      .that(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
      .isEqualTo("Not logged in")

    // 2. Should fallback to CustomUserStrings if missing in messages
    // "KailleraServerImpl.LoginMessage.1" is in language.properties but NOT messages.properties
    assertWithMessage("String missing in messages should fallback to CustomUserStrings")
      .that(EmuLang.getString("KailleraServerImpl.LoginMessage.1"))
      .isEqualTo("Welcome to a new EmuLinker-K Server!")
  }

  @Test
  fun `custom language behavior`() {
    // "custom" mode
    EmuLang.updateLanguage("custom")

    // 1. Should find key in CustomUserStrings (Priority)
    assertWithMessage("String from CustomUserStrings should be found")
      .that(EmuLang.getString("KailleraServerImpl.LoginMessage.1"))
      .isEqualTo("Welcome to a new EmuLinker-K Server!")

    // 2. Should fallback to messages.properties if missing in CustomUserStrings
    assertWithMessage("String missing in CustomUserStrings should fallback to messages")
      .that(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
      .isEqualTo("Not logged in")
  }

  @Test
  fun `unsupported language behavior`() {
    // "uk" (Ukrainian) is assumed to not exist -> Bundle will be null
    EmuLang.updateLanguage("uk")

    // 1. Should fallback to CustomUserStrings (because language bundle is missing)
    assertWithMessage(
        "String missing in language (missing bundle) should fallback to CustomUserStrings"
      )
      .that(EmuLang.getString("KailleraServerImpl.LoginMessage.1"))
      .isEqualTo("Welcome to a new EmuLinker-K Server!")

    // 2. Should fallback to Default Bundle (Root) eventually
    assertWithMessage("String missing in language and custom should fallback to Default")
      .that(EmuLang.getString("KailleraServerImpl.NotLoggedIn"))
      .isEqualTo("Not logged in")
  }

  @Test
  fun `missing key behavior`() {
    EmuLang.updateLanguage("en")
    val key = "NonExistentKey.XYZ"
    // Should return the key itself
    assertThat(EmuLang.getString(key)).isEqualTo(key)
  }
}
