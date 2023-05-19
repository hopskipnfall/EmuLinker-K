package org.emulinker.net

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.google.common.flogger.FluentLogger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.inject.Named
import org.emulinker.util.EmuUtil.formatSocketAddress

abstract class PrivateUDPServer(
  shutdownOnExit: Boolean,
  val remoteInetAddress: InetAddress,
  metrics: MetricRegistry,
  @Named("listeningOnPortsCounter") listeningOnPortsCounter: Counter
) : UDPServer(shutdownOnExit, metrics, listeningOnPortsCounter) {

  private val clientRequestTimer: Timer

  var remoteSocketAddress: InetSocketAddress? = null
    private set

  override fun handleReceived(buffer: ByteBuffer, remoteSocketAddress: InetSocketAddress) {
    if (this.remoteSocketAddress == null) this.remoteSocketAddress = remoteSocketAddress
    else if (remoteSocketAddress != this.remoteSocketAddress) {
      logger
        .atWarning()
        .log(
          "Rejecting packet received from wrong address: %s != %s",
          formatSocketAddress(remoteSocketAddress),
          formatSocketAddress(this.remoteSocketAddress!!)
        )
      return
    }
    clientRequestTimer.time().use { handleReceived(buffer) }
  }

  protected abstract fun handleReceived(buffer: ByteBuffer)

  protected fun send(buffer: ByteBuffer?) {
    super.send(buffer, remoteSocketAddress)
  }

  init {
    clientRequestTimer = metrics.timer(MetricRegistry.name(this.javaClass, "UdpClientRequests"))
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
