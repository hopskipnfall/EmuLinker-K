package org.emulinker.net;

import com.codahale.metrics.MetricRegistry;
import com.google.common.flogger.FluentLogger;
import java.net.*;
import java.nio.ByteBuffer;
import org.emulinker.util.*;

public abstract class PrivateUDPServer extends UDPServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private InetAddress remoteAddress;
  private InetSocketAddress remoteSocketAddress;

  public PrivateUDPServer(
      boolean shutdownOnExit, InetAddress remoteAddress, MetricRegistry metrics) {
    super(shutdownOnExit, metrics);
    this.remoteAddress = remoteAddress;
  }

  public InetAddress getRemoteInetAddress() {
    return remoteAddress;
  }

  public InetSocketAddress getRemoteSocketAddress() {
    return remoteSocketAddress;
  }

  @Override
  protected void handleReceived(ByteBuffer buffer, InetSocketAddress inboundSocketAddress) {
    if (remoteSocketAddress == null) remoteSocketAddress = inboundSocketAddress;
    else if (!inboundSocketAddress.equals(remoteSocketAddress)) {
      logger.atWarning().log(
          "Rejecting packet received from wrong address: "
              + EmuUtil.formatSocketAddress(inboundSocketAddress)
              + " != "
              + EmuUtil.formatSocketAddress(remoteSocketAddress));
      return;
    }

    handleReceived(buffer);
  }

  protected abstract void handleReceived(ByteBuffer buffer);

  protected void send(ByteBuffer buffer) {
    super.send(buffer, remoteSocketAddress);
  }
}
