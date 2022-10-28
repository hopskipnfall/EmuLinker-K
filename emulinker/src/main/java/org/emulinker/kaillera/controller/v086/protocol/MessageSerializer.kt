package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer

sealed interface MessageSerializer<E : V086Message> {
  fun read(buffer: ByteBuffer, messageNumber: Int): MessageParseResult<E>

  fun write(buffer: ByteBuffer, message: E)
}
