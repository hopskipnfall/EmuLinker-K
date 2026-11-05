package org.emulinker.kaillera.controller.v086.action

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.kaillera.model.exception.ActionException
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AdminCommandActionTest :
  FunSpec({
    val userAddress = InetAddress.getByName("10.1.2.3")
    val user: KailleraUser = mock {
      on { id } doReturn 5
      on { name } doReturn "Bob"
      on { connectSocketAddress } doReturn InetSocketAddress(userAddress, 27000)
    }
    val server: KailleraServer = mock {
      on { getUser(5) } doReturn user
      on { usersMap } doReturn ConcurrentHashMap(mapOf(5 to user))
    }

    test("number is a user ID, not an IP address") {
      // InetAddress.getByName("5") is 0.0.0.5, which previously hid the user with ID 5.
      val (address, name) = AdminCommandAction.resolveTarget("5", server)

      address shouldBe userAddress
      name shouldBe "Bob"
    }

    test("unknown user ID fails") {
      shouldThrow<ActionException> { AdminCommandAction.resolveTarget("7", server) }
    }

    test("IP literal is used directly") {
      val (address, name) = AdminCommandAction.resolveTarget("192.168.0.9", server)

      address shouldBe InetAddress.getByName("192.168.0.9")
      name.shouldBeNull()
    }

    test("user name matches ignoring case") {
      val (address, name) = AdminCommandAction.resolveTarget("bOb", server)

      address shouldBe userAddress
      name shouldBe "Bob"
    }
  })
