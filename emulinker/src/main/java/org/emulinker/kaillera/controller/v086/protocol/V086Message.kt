package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.flogger.FluentLogger
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.ByteBufferMessage
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.util.UnsignedUtil.putUnsignedShort

/**
 * A message in the v086 Kaillera protocol.
 *
 * A message structure over the wire is as follows:
 * 1. The message number (2 bytes)
 * 2. The length of the message (2 bytes)
 * 3. The ID of the message type (1 byte)
 * 4. All body bytes written by [writeBodyTo] ([bodyBytes] bytes)
 */
abstract class V086Message : ByteBufferMessage() {
  /**
   * The 0-based enumeration indicating the order in which this message was sent/received for each
   * server.
   *
   * Any two messages from the same client with the same [messageNumber] can be assumed to be
   * identical.
   *
   * The first client->server message would be 0 and the first server->client message would be 0.
   * These are two separate counters.
   *
   * This rule is sometimes broken. For instance, ConnectMessage_HELLO and ConnectMessage_HELLOD00D
   * are not included in this count.
   */
  abstract val messageNumber: Int

  // TODO(nue): Think about this:
  //  @Deprecated("We should try to use a sealed class instead of relying on this messageId field")
  abstract val messageTypeId: Byte

  /** The total number of bytes the message takes up, including the message ID byte. */
  override val bodyBytesPlusMessageIdType: Int
    get() = bodyBytes + 1

  /** Gets the number of bytes to represent the string in the charset defined in emulinker.config */
  protected fun getNumBytes(s: String): Int {
    return s.toByteArray(AppModule.charsetDoNotUse).size
  }

  /** Number of bytes the body of the message takes up (excluding the message ID byte). */
  abstract val bodyBytes: Int

  override fun writeTo(buffer: ByteBuffer) {
    val len = bodyBytesPlusMessageIdType
    if (len > buffer.remaining()) {
      logger
        .atWarning()
        .log(
          "Ran out of output buffer space, consider increasing the controllers.v086.bufferSize setting!"
        )
    } else {
      buffer.putUnsignedShort(messageNumber)
      // there no realistic reason to use unsigned here since a single packet can't be that large
      buffer.mark()
      buffer.putUnsignedShort(len)
      //		buffer.putShort((short)getLength());
      buffer.put(messageTypeId)
      writeBodyTo(buffer)
    }
  }

  protected abstract fun writeBodyTo(buffer: ByteBuffer)

  companion object {
    val SERIALIZERS: Map<Byte, MessageSerializer<out V086Message>> =
      arrayOf(
          Ack.ClientAckSerializer,
          Ack.ServerAckSerializer,
          AllReady.AllReadySerializer,
          CachedGameData.CachedGameDataSerializer,
          Chat.ChatSerializer,
          CloseGame.CloseGameSerializer,
          ConnectionRejected.ConnectionRejectedSerializer,
          CreateGame.CreateGameSerializer,
          GameChat.GameChatSerializer,
          GameData.GameDataSerializer,
          GameKick.GameKickSerializer,
          GameStatus.GameStatusSerializer,
          InformationMessage.InformationMessageSerializer,
          JoinGame.JoinGameSerializer,
          KeepAlive.KeepAliveSerializer,
          PlayerDrop.PlayerDropSerializer,
          PlayerInformation.PlayerInformationSerializer,
          Quit.QuitSerializer,
          QuitGame.QuitGameSerializer,
          ServerStatus.ServerStatusSerializer,
          StartGame.StartGameSerializer,
          UserInformation.UserInformationSerializer,
          UserJoined.UserJoinedSerializer,
        )
        .associateBy { it.messageTypeId }

    protected fun <T : V086Message> T.validateMessageNumber(): MessageParseResult<T> {
      return if (this.messageNumber !in 0..0xFFFF) {
        return MessageParseResult.Failure("Invalid message number: ${this.messageNumber}")
      } else {
        MessageParseResult.Success(this)
      }
    }

    @Throws(ParseException::class, MessageFormatException::class)
    fun parse(messageNumber: Int, messageLength: Int, buffer: ByteBuffer): V086Message {

      val messageType = buffer.get()

      val serializer =
        checkNotNull(SERIALIZERS[messageType]) { "Unrecognized message ID: $messageType" }

      var parseResult: MessageParseResult<out V086Message> = serializer.read(buffer, messageNumber)
      if (parseResult is MessageParseResult.Success) {
        parseResult = parseResult.message.validateMessageNumber()
      }

      val message =
        when (parseResult) {
          // TODO(nue): Return this up the stack instead of throwing an exception.
          is MessageParseResult.Failure -> throw MessageFormatException(parseResult.toString())
          is MessageParseResult.Success -> parseResult.message
        }

      // removed to improve speed
      if (message.bodyBytesPlusMessageIdType != messageLength) {
        //			throw new ParseException("Bundle contained length " + messageLength + " !=  parsed
        // lengthy
        // " + message.getLength());
        logger
          .atFine()
          .log(
            "Bundle contained length %d != parsed length %d",
            messageLength,
            message.bodyBytesPlusMessageIdType
          )
      }
      return message
    }

    private val logger = FluentLogger.forEnclosingClass()
  }
}

/** [V086Message] that is sent from the server to clients. */
sealed interface ServerMessage

/** [V086Message] that is sent from clients to the server. */
sealed interface ClientMessage
