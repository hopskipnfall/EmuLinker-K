package org.emulinker.kaillera.controller

import org.emulinker.kaillera.controller.v086.protocol.ChatNotification
import org.emulinker.kaillera.controller.v086.protocol.ChatRequest
import org.emulinker.kaillera.controller.v086.protocol.CreateGameNotification
import org.emulinker.kaillera.controller.v086.protocol.InformationMessage
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.junit.Test
import org.koin.core.component.get

class ShadowbanE2ETest : E2ETestBase() {

  @Test
  fun testShadowbannedUserChatIsHiddenFromNormalUser() {
    val normalUserA = Client(1, "NormalUserA", this)
    val shadowUserB = Client(2, "ShadowUserB", this)
    val adminUser =
      Client(
        3,
        "AdminUser",
        this,
      ) // Also acts as a normal user in terms of chat receiving unless logic dictates otherwise,
    // but let's just stick to A and B to clear up variables

    // 1. Set Access Levels (admin can only ban users with lower permissions)
    val accessManager = get<org.emulinker.kaillera.access.AccessManager>() as FakeAccessManager
    accessManager.accessLevels["127.0.0.1"] =
      org.emulinker.kaillera.access.AccessManager.ACCESS_NORMAL
    accessManager.accessLevels["127.0.0.2"] =
      org.emulinker.kaillera.access.AccessManager.ACCESS_NORMAL
    accessManager.accessLevels["127.0.0.3"] =
      org.emulinker.kaillera.access.AccessManager.ACCESS_SUPERADMIN

    // 2. Connect clients
    normalUserA.login()
    shadowUserB.login()
    adminUser.login()

    // Grant admin access to adminUser's IP (they all share 127.0.0.1, so all are admins right now
    // in FakeAccessManager by default!)
    // Yes: getAccess returns ACCESS_ADMIN globally in FakeAccessManager.

    // Wait for the login handshakes to settle and the 2-second chat flood protection to expire
    Thread.sleep(2500)

    // Admin verifies they can talk to the server
    adminUser.sendGlobalChat("/alivecheck")
    adminUser.consumeUntil(timeoutMs = 2000) {
      it is InformationMessage && it.message.contains("Alive Check")
    }

    // Normal User A sends a message
    normalUserA.sendGlobalChat("Hello from Normal A")

    // Verify B receives it
    shadowUserB.consumeUntil {
      it is ChatNotification && it.message.contains("Hello from Normal A")
    }

    // Specifically shadowban User B (who is ID 2)
    adminUser.sendGlobalChat("/shadowban 2")

    // Admin should get a confirmation
    adminUser.consumeUntil { it is InformationMessage && it.message.contains("Shadowban applied") }

    // Shadow User B sends a message
    shadowUserB.sendGlobalChat("Hello from Shadow B")

    // Verify B receives their own message? No, Kaillera Server echoes chat to everyone, but
    // standard users don't see it if it's blocked.
    // Actually, EmuLinker-K broadcats ChatEvent. Let's see if A receives it.
    // We expect an empty queue or timeout for A
    normalUserA.verifyNoMessage(timeoutMs = 500) {
      it is ChatNotification && it.message.contains("Hello from Shadow B")
    }

    // Normal User A creates a game
    val gameId = normalUserA.createGame()

    // Shadow User B should NOT see the game creation
    shadowUserB.verifyNoMessage(timeoutMs = 500) {
      it is CreateGameNotification && it.gameId == gameId
    }

    normalUserA.quit()
    shadowUserB.quit()
    adminUser.quit()
  }
}

// Extension to Client to send global chat easily
fun E2ETestBase.Client.sendGlobalChat(message: String) {
  sendBundle(V086Bundle.Single(ChatRequest(++lastMessageNumber, message)))
}
