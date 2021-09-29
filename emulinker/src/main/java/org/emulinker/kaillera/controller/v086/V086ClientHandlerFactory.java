package org.emulinker.kaillera.controller.v086;

import dagger.assisted.AssistedFactory;
import java.net.InetSocketAddress;

@AssistedFactory
public interface V086ClientHandlerFactory {
    public V086ClientHandler create(InetSocketAddress remoteSocketAddress, V086Controller v086Controller);
}