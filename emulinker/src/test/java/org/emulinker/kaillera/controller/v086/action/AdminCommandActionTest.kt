package org.emulinker.kaillera.controller.v086.action

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.exception.ActionException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AdminCommandActionTest {
  private val userAddress = InetAddress.getByName("10.1.2.3")
  private val user: KailleraUser = mock {
    on { id } doReturn 5
    on { name } doReturn "Bob"
    on { connectSocketAddress } doReturn InetSocketAddress(userAddress, 27000)
  }
  private val server: KailleraServer = mock {
    on { getUser(5) } doReturn user
    on { usersMap } doReturn ConcurrentHashMap(mapOf(5 to user))
  }

  @Test
  fun numberIsAUserIdNotAnIpAddress() {
    // InetAddress.getByName("5") is 0.0.0.5, which previously hid the user with ID 5.
    val (address, name) = AdminCommandAction.resolveTarget("5", server)

    assertThat(address).isEqualTo(userAddress)
    assertThat(name).isEqualTo("Bob")
  }

  @Test
  fun unknownUserIdFails() {
    assertThrows(ActionException::class.java) { AdminCommandAction.resolveTarget("7", server) }
  }

  @Test
  fun ipLiteralIsUsedDirectly() {
    val (address, name) = AdminCommandAction.resolveTarget("192.168.0.9", server)

    assertThat(address).isEqualTo(InetAddress.getByName("192.168.0.9"))
    assertThat(name).isNull()
  }

  @Test
  fun userNameMatchesIgnoringCase() {
    val (address, name) = AdminCommandAction.resolveTarget("bOb", server)

    assertThat(address).isEqualTo(userAddress)
    assertThat(name).isEqualTo("Bob")
  }
}
