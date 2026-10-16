package org.emulinker.kaillera.access

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class ConnectionGuardTest {
  private var nowNs = 1_000_000_000L

  private fun guard(perMinute: Int = 6, violations: Int = 3) =
    ConnectionGuard(perMinute, violations) { nowNs }

  @Test
  fun allowsABurstUpToTheLimitThenRefuses() {
    val guard = guard(perMinute = 6)

    repeat(6) { assertThat(guard.allowLogin("a")).isTrue() }
    assertThat(guard.allowLogin("a")).isFalse()
  }

  @Test
  fun addressesAreIndependent() {
    val guard = guard(perMinute = 2)
    repeat(2) { guard.allowLogin("a") }

    assertThat(guard.allowLogin("a")).isFalse()
    assertThat(guard.allowLogin("b")).isTrue()
  }

  @Test
  fun allowanceRefillsOverTime() {
    val guard = guard(perMinute = 6)
    repeat(6) { guard.allowLogin("a") }
    assertThat(guard.allowLogin("a")).isFalse()

    // 6 per minute is one every 10 seconds.
    nowNs += 10.seconds.inWholeNanoseconds
    assertThat(guard.allowLogin("a")).isTrue()
    assertThat(guard.allowLogin("a")).isFalse()
  }

  @Test
  fun aSlowAndSteadyUserIsNeverRefused() {
    val guard = guard(perMinute = 6)

    repeat(100) {
      nowNs += 15.seconds.inWholeNanoseconds
      assertThat(guard.allowLogin("a")).isTrue()
    }
  }

  @Test
  fun zeroDisablesTheRateLimit() {
    val guard = guard(perMinute = 0)

    repeat(1000) { assertThat(guard.allowLogin("a")).isTrue() }
  }

  @Test
  fun violationsWithinAMinuteTriggerABanThenRestart() {
    val guard = guard(violations = 3)

    assertThat(guard.recordViolation("a")).isFalse()
    assertThat(guard.recordViolation("a")).isFalse()
    assertThat(guard.recordViolation("a")).isTrue()
    // The count starts over after a ban was requested.
    assertThat(guard.recordViolation("a")).isFalse()
  }

  @Test
  fun slowViolationsDoNotAddUp() {
    val guard = guard(violations = 3)

    repeat(10) {
      nowNs += 40.seconds.inWholeNanoseconds
      assertThat(guard.recordViolation("a")).isFalse()
      nowNs += 40.seconds.inWholeNanoseconds
      assertThat(guard.recordViolation("a")).isFalse()
    }
  }

  @Test
  fun zeroNeverBans() {
    val guard = guard(violations = 0)

    repeat(1000) { assertThat(guard.recordViolation("a")).isFalse() }
  }

  @Test
  fun ipv4IsGroupedByAddress() {
    assertThat(ConnectionGuard.keyOf(InetAddress.getByName("203.0.113.7"))).isEqualTo("203.0.113.7")
    assertThat(ConnectionGuard.keyOf(InetAddress.getByName("203.0.113.8")))
      .isNotEqualTo(ConnectionGuard.keyOf(InetAddress.getByName("203.0.113.7")))
  }

  @Test
  fun ipv6IsGroupedBySlash64() {
    val a = InetAddress.getByName("2001:db8:1:2:aaaa:bbbb:cccc:dddd")
    val b = InetAddress.getByName("2001:db8:1:2:1111:2222:3333:4444")
    val other = InetAddress.getByName("2001:db8:1:3::1")

    assertThat(ConnectionGuard.keyOf(a)).isEqualTo(ConnectionGuard.keyOf(b))
    assertThat(ConnectionGuard.keyOf(a)).isNotEqualTo(ConnectionGuard.keyOf(other))
  }

  @Test
  fun ipv4MappedIpv6IsTreatedAsIpv4() {
    val mapped =
      InetAddress.getByAddress(ByteArray(10) + byteArrayOf(-1, -1, 203.toByte(), 0, 113, 7))

    assertThat(ConnectionGuard.keyOf(mapped)).isEqualTo("203.0.113.7")
  }

  @Test
  fun banPatternMatchesTheWholeGroup() {
    assertThat(ConnectionGuard.banPatternFor(InetAddress.getByName("203.0.113.7")))
      .isEqualTo("203.0.113.7")
    val pattern = ConnectionGuard.banPatternFor(InetAddress.getByName("2001:db8:1:2::9"))
    assertThat(pattern).isEqualTo("2001:db8:1:2:*")
    // The access manager matches wildcard patterns against Inet6Address.hostAddress.
    val member = InetAddress.getByName("2001:db8:1:2:aaaa:bbbb:cccc:dddd").hostAddress
    assertThat(org.emulinker.util.WildcardStringPattern(pattern).match(member)).isTrue()
    val outsider = InetAddress.getByName("2001:db8:1:3::1").hostAddress
    assertThat(org.emulinker.util.WildcardStringPattern(pattern).match(outsider)).isFalse()
  }

  @Test
  fun purgeForgetsIdleAddressesAndRestoresTheirAllowance() {
    val guard = guard(perMinute = 2)
    repeat(2) { guard.allowLogin("a") }
    assertThat(guard.allowLogin("a")).isFalse()

    nowNs += 10 * 60 * 1_000_000_000L
    guard.purgeIdle()

    assertThat(guard.allowLogin("a")).isTrue()
  }
}
