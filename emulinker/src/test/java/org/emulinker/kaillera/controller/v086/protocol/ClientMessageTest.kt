package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [org.emulinker.kaillera.controller.v086.V086ClientHandler] drops anything from a client that is
 * not a [ClientMessage], so every message a real client sends must be marked as one.
 */
class ClientMessageTest {
  @Test
  fun messagesSentByClientsAreClientMessages() {
    val clientSent: List<V086Message> =
      listOf(
        QuitRequest(0, "bye"),
        ChatRequest(0, "hi"),
        GameChatRequest(0, "hi"),
        ClientAck(0),
        KeepAlive(0, 0),
        AllReady(0),
        StartGameRequest(0),
        QuitGameRequest(0),
        PlayerDropRequest(0),
        GameKick(0, 1),
      )

    for (message in clientSent) {
      assertThat(message).isInstanceOf(ClientMessage::class.java)
    }
  }
}
