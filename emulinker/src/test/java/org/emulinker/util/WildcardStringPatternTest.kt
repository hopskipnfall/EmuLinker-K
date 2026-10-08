package org.emulinker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WildcardStringPatternTest {
  private fun matches(pattern: String, s: String) = WildcardStringPattern(pattern).match(s)

  @Test
  fun exactPattern() {
    assertThat(matches("10.0.0.1", "10.0.0.1")).isTrue()
    assertThat(matches("10.0.0.1", "10.0.0.10")).isFalse()
  }

  @Test
  fun starMatchesAnyNonBlankString() {
    assertThat(matches("*", "anything")).isTrue()
    assertThat(matches("*", "")).isFalse()
    assertThat(matches("*", "  ")).isFalse()
  }

  @Test
  fun prefixSuffixAndContains() {
    assertThat(matches("192.168.*", "192.168.1.5")).isTrue()
    assertThat(matches("192.168.*", "10.168.1.5")).isFalse()
    assertThat(matches("*.1.5", "192.168.1.5")).isTrue()
    assertThat(matches("*.1.5", "192.168.1.6")).isFalse()
    assertThat(matches("*168*", "192.168.1.5")).isTrue()
    assertThat(matches("*999*", "192.168.1.5")).isFalse()
  }

  @Test
  fun octetWildcardsInTheMiddle() {
    assertThat(matches("10.*.*.1", "10.20.30.1")).isTrue()
    assertThat(matches("10.*.*.1", "10.20.30.2")).isFalse()
  }

  @Test
  fun partsMustNotOverlap() {
    // The shared "b" must not be used for both the start and the end.
    assertThat(matches("ab*bc", "abc")).isFalse()
    assertThat(matches("ab*bc", "abbc")).isTrue()
    assertThat(matches("a*a", "a")).isFalse()
    assertThat(matches("a*a", "aa")).isTrue()
    // The contains part may not reuse characters of the suffix either.
    assertThat(matches("x*ab*b", "xab")).isFalse()
    assertThat(matches("x*ab*b", "xabb")).isTrue()
  }

  @Test
  fun containsPartsMatchInOrderWithoutReuse() {
    assertThat(matches("*ab*cd*", "abcd")).isTrue()
    assertThat(matches("*ab*cd*", "cdab")).isFalse()
    assertThat(matches("*aa*aa*", "aaa")).isFalse()
    assertThat(matches("*aa*aa*", "aaaa")).isTrue()
  }

  @Test
  fun emptyPatternMatchesAnyNonBlankString() {
    assertThat(matches("", "1.2.3.4")).isTrue()
    assertThat(matches("", "")).isFalse()
  }
}
