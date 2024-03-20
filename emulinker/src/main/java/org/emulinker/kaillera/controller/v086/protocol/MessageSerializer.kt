package org.emulinker.kaillera.controller.v086.protocol

import io.ktor.utils.io.core.ByteReadPacket
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.ParseException

sealed interface MessageSerializer<E : V086Message> {
  val messageTypeId: Byte

  fun read(buffer: ByteBuffer, messageNumber: Int): Result<E>

  fun read(packet: ByteReadPacket, messageNumber: Int): Result<E>

  fun write(buffer: ByteBuffer, message: E)

  fun write(buffer: ByteBuf, message: E)
}

fun <T : V086Message> parseFailure(string: String): Result<T> =
  Result.failure(ParseException(string))
