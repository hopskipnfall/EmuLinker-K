package org.emulinker.net

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

class UdpSocketProvider @Inject constructor() {
  fun bindSocket(inetSocketAddress: InetSocketAddress, bufferSize: Int): BoundDatagramSocket {
    if (selectorManager == null) {
      selectorManager = SelectorManager(Dispatchers.IO)
    }
    return aSocket(selectorManager!!)
      .udp()
      .configure {
        receiveBufferSize = bufferSize
        sendBufferSize = bufferSize
        typeOfService = TypeOfService.IPTOS_LOWDELAY
      }
      .bind(inetSocketAddress)
  }

  companion object {
    // TODO(nue): Make this class a singleton and make this an instance variable.
    private var selectorManager: SelectorManager? = null
  }
}
