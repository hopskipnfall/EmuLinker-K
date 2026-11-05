package org.emulinker.kaillera.controller.v086.protocol

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * [org.emulinker.kaillera.controller.v086.V086ClientHandler] drops anything from a client that is
 * not a [ClientMessage], so every message a real client sends must be marked as one.
 */
class ClientMessageTest :
  FunSpec({
    context("messages sent by clients are ClientMessages") {
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

      withTests(nameFn = { it::class.simpleName ?: it.toString() }, clientSent) { message ->
        message.shouldBeInstanceOf<ClientMessage>()
      }
    }
  })
