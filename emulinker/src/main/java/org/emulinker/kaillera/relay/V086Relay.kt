package org.emulinker.kaillera.relay

import com.google.common.flogger.FluentLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.net.InetSocketAddress
import java.nio.Buffer
import java.nio.ByteBuffer
import org.emulinker.kaillera.controller.messaging.MessageFormatException
import org.emulinker.kaillera.controller.messaging.ParseException
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle.Companion.parse
import org.emulinker.kaillera.controller.v086.protocol.V086BundleFormatException
import org.emulinker.net.UDPRelay
import org.emulinker.util.EmuUtil.dumpBuffer

// TODO(jonnjonn): I think this can be deleted??
class V086Relay
@AssistedInject
internal constructor(
  @Assisted listenPort: Int,
  @Assisted serverSocketAddress: InetSocketAddress,
) : UDPRelay(listenPort, serverSocketAddress) {
  private var lastServerMessageNumber = -1
  private var lastClientMessageNumber = -1

  @AssistedFactory
  interface Factory {
    fun create(listenPort: Int, serverSocketAddress: InetSocketAddress?): V086Relay?
  }

  override fun toString() =
    "Kaillera client datagram relay version 0.86 on port ${super.listenPort}"

  override fun processClientToServer(
    receiveBuffer: ByteBuffer,
    fromAddress: InetSocketAddress,
    toAddress: InetSocketAddress
  ): ByteBuffer? {
    //    logger.atFine().log("-> " + dumpBuffer(receiveBuffer))
    val inBundle: V086Bundle =
      try {
        // inBundle = V086Bundle.parse(receiveBuffer, lastClientMessageNumber);
        parse(receiveBuffer, -1)
      } catch (e: ParseException) {
        receiveBuffer.rewind()
        logger.atWarning().withCause(e).log("Failed to parse: %s", dumpBuffer(receiveBuffer))
        return null
      } catch (e: V086BundleFormatException) {
        receiveBuffer.rewind()
        logger
          .atWarning()
          .withCause(e)
          .log("Invalid message bundle format: %s", dumpBuffer(receiveBuffer))
        return null
      } catch (e: MessageFormatException) {
        receiveBuffer.rewind()
        logger.atWarning().withCause(e).log("Invalid message format: %s", dumpBuffer(receiveBuffer))
        return null
      }
    //    logger.atInfo().log("-> %s", $inBundle)
    val inMessages = inBundle.messages
    for (i in 0 until inBundle.numMessages) {
      if (inMessages[i]!!.messageNumber > lastClientMessageNumber)
        lastClientMessageNumber = inMessages[i]!!.messageNumber
    }
    val sendBuffer = ByteBuffer.allocate(receiveBuffer.limit())
    receiveBuffer.rewind()
    sendBuffer.put(receiveBuffer)
    // Cast to avoid issue with java version mismatch: https://stackoverflow.com/a/61267496/2875073
    (sendBuffer as Buffer).flip()
    return sendBuffer
  }

  override fun processServerToClient(
    receiveBuffer: ByteBuffer,
    fromAddress: InetSocketAddress,
    toAddress: InetSocketAddress
  ): ByteBuffer? {
    //    logger.atFine().log("<- " + dumpBuffer(receiveBuffer))
    val inBundle: V086Bundle =
      try {
        // inBundle = V086Bundle.parse(receiveBuffer, lastServerMessageNumber);
        parse(receiveBuffer, -1)
      } catch (e: ParseException) {
        receiveBuffer.rewind()
        logger.atWarning().withCause(e).log("Failed to parse: %s", dumpBuffer(receiveBuffer))
        return null
      } catch (e: V086BundleFormatException) {
        receiveBuffer.rewind()
        logger
          .atWarning()
          .withCause(e)
          .log("Invalid message bundle format: %s", dumpBuffer(receiveBuffer))
        return null
      } catch (e: MessageFormatException) {
        receiveBuffer.rewind()
        logger.atWarning().withCause(e).log("Invalid message format: %s", dumpBuffer(receiveBuffer))
        return null
      }
    //    logger.atInfo().log("<- %s", $inBundle)
    val inMessages = inBundle.messages
    for (i in 0 until inBundle.numMessages) {
      if (inMessages[i]!!.messageNumber > lastServerMessageNumber)
        lastServerMessageNumber = inMessages[i]!!.messageNumber
    }
    val sendBuffer = ByteBuffer.allocate(receiveBuffer.limit())
    receiveBuffer.rewind()
    sendBuffer.put(receiveBuffer)
    // Cast to avoid issue with java version mismatch: https://stackoverflow.com/a/61267496/2875073
    (sendBuffer as Buffer).flip()
    return sendBuffer
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
