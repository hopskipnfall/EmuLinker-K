package org.emulinker.kaillera.controller.v086.protocol

import java.nio.ByteBuffer
import kotlin.reflect.KProperty0
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.V086Utils.getNumBytesPlusStopByte
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.util.EmuUtil
import org.emulinker.util.UnsignedUtil.getUnsignedInt
import org.emulinker.util.UnsignedUtil.getUnsignedShort
import org.emulinker.util.UnsignedUtil.putUnsignedInt
import org.emulinker.util.UnsignedUtil.putUnsignedShort

sealed class Instruction<E>(internal val property: KProperty0<E>) {
  abstract fun get(buffer: ByteBuffer): E
  abstract fun set(buffer: ByteBuffer)

  abstract fun numBytes(): Int
}

class SingleByteInstruction(property: KProperty0<Byte>) : Instruction<Byte>(property) {
  override fun numBytes() = V086Utils.Bytes.SINGLE_BYTE

  override fun get(buffer: ByteBuffer): Byte = buffer.get()

  override fun set(buffer: ByteBuffer) {
    buffer.put(property.get())
  }
}

class UnsignedShortInstruction(property: KProperty0<Int>) : Instruction<Int>(property) {
  override fun numBytes() = V086Utils.Bytes.SHORT

  override fun get(buffer: ByteBuffer): Int = buffer.getUnsignedShort()

  override fun set(buffer: ByteBuffer) {
    buffer.putUnsignedShort(property.get())
  }
}

class UnsignedIntegerInstruction(property: KProperty0<Long>) : Instruction<Long>(property) {
  override fun numBytes() = V086Utils.Bytes.INTEGER

  override fun get(buffer: ByteBuffer): Long = buffer.getUnsignedInt()

  override fun set(buffer: ByteBuffer) {
    buffer.putUnsignedInt(property.get())
  }
}

class StringInstruction(property: KProperty0<String>) : Instruction<String>(property) {
  override fun numBytes() = property.get().getNumBytesPlusStopByte()

  override fun get(buffer: ByteBuffer): String = EmuUtil.readString(buffer)

  override fun set(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, property.get())
  }
}

data class UserInformation
@Throws(MessageFormatException::class)
constructor(
  override val messageNumber: Int,
  val username: String,
  val clientType: String,
  val connectionType: ConnectionType
) : V086Message() {
  private val connectionTypeByte = connectionType.byteValue

  val instructions =
    arrayOf(
      StringInstruction(this::username),
      StringInstruction(this::clientType),
      SingleByteInstruction(this::connectionTypeByte)
    )

  override val messageTypeId = ID

  override val bodyBytes: Int =
    username.getNumBytesPlusStopByte() +
      clientType.getNumBytesPlusStopByte() +
      V086Utils.Bytes.SINGLE_BYTE

  public override fun writeBodyTo(buffer: ByteBuffer) {
    EmuUtil.writeString(buffer, username)
    EmuUtil.writeString(buffer, clientType)
    buffer.put(connectionType.byteValue)
  }

  companion object {
    const val ID: Byte = 0x03

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, buffer: ByteBuffer): MessageParseResult<UserInformation> {
      if (buffer.remaining() < 5) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val userName = EmuUtil.readString(buffer)
      if (buffer.remaining() < 3) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val clientType = EmuUtil.readString(buffer)
      if (buffer.remaining() < 1) {
        return MessageParseResult.Failure("Failed byte count validation!")
      }
      val connectionType = buffer.get()
      return MessageParseResult.Success(
        UserInformation(
          messageNumber,
          userName,
          clientType,
          ConnectionType.fromByteValue(connectionType)
        )
      )
    }

    object UserInformationSerializer : MessageSerializer<UserInformation> {
      override val messageTypeId: Byte = ID

      override fun read(
        buffer: ByteBuffer,
        messageNumber: Int
      ): MessageParseResult<UserInformation> {
        TODO("Not yet implemented")
      }

      override fun write(buffer: ByteBuffer, message: UserInformation) {
        TODO("Not yet implemented")
      }
    }
  }
}
