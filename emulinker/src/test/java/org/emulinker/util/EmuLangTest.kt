package org.emulinker.util

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmuLangTest :
  FunSpec({
    test("default language behavior") {
      // "en" or any standard language
      EmuLang.updateLanguage("en")

      // 1. Should find key in messages.properties
      withClue("String from messages.properties should be found") {
        EmuLang.getString("KailleraServerImpl.NotLoggedIn") shouldBe "Not logged in"
      }

      // 2. Should fallback to CustomUserStrings if missing in messages
      // "KailleraServerImpl.LoginMessage.1" is in language.properties but NOT messages.properties
      withClue("String missing in messages should fallback to CustomUserStrings") {
        EmuLang.getString("KailleraServerImpl.LoginMessage.1") shouldBe
          "Welcome to a new EmuLinker-K Server!"
      }
    }

    test("custom language behavior") {
      // "custom" mode
      EmuLang.updateLanguage("custom")

      // 1. Should find key in CustomUserStrings (Priority)
      withClue("String from CustomUserStrings should be found") {
        EmuLang.getString("KailleraServerImpl.LoginMessage.1") shouldBe
          "Welcome to a new EmuLinker-K Server!"
      }

      // 2. Should fallback to messages.properties if missing in CustomUserStrings
      withClue("String missing in CustomUserStrings should fallback to messages") {
        EmuLang.getString("KailleraServerImpl.NotLoggedIn") shouldBe "Not logged in"
      }
    }

    test("unsupported language behavior") {
      // "uk" (Ukrainian) is assumed to not exist -> Bundle will be null
      EmuLang.updateLanguage("uk")

      // 1. Should fallback to CustomUserStrings (because language bundle is missing)
      withClue("String missing in language (missing bundle) should fallback to CustomUserStrings") {
        EmuLang.getString("KailleraServerImpl.LoginMessage.1") shouldBe
          "Welcome to a new EmuLinker-K Server!"
      }

      // 2. Should fallback to Default Bundle (Root) eventually
      withClue("String missing in language and custom should fallback to Default") {
        EmuLang.getString("KailleraServerImpl.NotLoggedIn") shouldBe "Not logged in"
      }
    }

    test("missing key behavior") {
      EmuLang.updateLanguage("en")
      val key = "NonExistentKey.XYZ"
      // Should return the key itself
      EmuLang.getString(key) shouldBe key
    }
  })
