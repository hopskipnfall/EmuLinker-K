package org.emulinker.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class WildcardStringPatternTest :
  FunSpec({
    fun matches(pattern: String, s: String) = WildcardStringPattern(pattern).match(s)

    test("exact pattern") {
      matches("10.0.0.1", "10.0.0.1").shouldBeTrue()
      matches("10.0.0.1", "10.0.0.10").shouldBeFalse()
    }

    test("star matches any non-blank string") {
      matches("*", "anything").shouldBeTrue()
      matches("*", "").shouldBeFalse()
      matches("*", "  ").shouldBeFalse()
    }

    test("prefix, suffix, and contains") {
      matches("192.168.*", "192.168.1.5").shouldBeTrue()
      matches("192.168.*", "10.168.1.5").shouldBeFalse()
      matches("*.1.5", "192.168.1.5").shouldBeTrue()
      matches("*.1.5", "192.168.1.6").shouldBeFalse()
      matches("*168*", "192.168.1.5").shouldBeTrue()
      matches("*999*", "192.168.1.5").shouldBeFalse()
    }

    test("octet wildcards in the middle") {
      matches("10.*.*.1", "10.20.30.1").shouldBeTrue()
      matches("10.*.*.1", "10.20.30.2").shouldBeFalse()
    }

    test("parts must not overlap") {
      // The shared "b" must not be used for both the start and the end.
      matches("ab*bc", "abc").shouldBeFalse()
      matches("ab*bc", "abbc").shouldBeTrue()
      matches("a*a", "a").shouldBeFalse()
      matches("a*a", "aa").shouldBeTrue()
      // The contains part may not reuse characters of the suffix either.
      matches("x*ab*b", "xab").shouldBeFalse()
      matches("x*ab*b", "xabb").shouldBeTrue()
    }

    test("contains parts match in order without reuse") {
      matches("*ab*cd*", "abcd").shouldBeTrue()
      matches("*ab*cd*", "cdab").shouldBeFalse()
      matches("*aa*aa*", "aaa").shouldBeFalse()
      matches("*aa*aa*", "aaaa").shouldBeTrue()
    }

    test("empty pattern matches any non-blank string") {
      matches("", "1.2.3.4").shouldBeTrue()
      matches("", "").shouldBeFalse()
    }
  })
