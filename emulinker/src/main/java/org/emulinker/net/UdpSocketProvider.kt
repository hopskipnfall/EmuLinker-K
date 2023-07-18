package org.emulinker.net

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.TypeOfService
import io.ktor.network.sockets.aSocket
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

class UdpSocketProvider @Inject constructor() {
  fun bindSocket(inetSocketAddress: InetSocketAddress, bufferSize: Int): BoundDatagramSocket {
    return aSocket(selectorManager)
      .udp()
      .configure {
        receiveBufferSize = bufferSize
        sendBufferSize = bufferSize
        typeOfService = TypeOfService.IPTOS_LOWDELAY
      }
      .bind(inetSocketAddress)
  }

  private companion object {
    @OptIn(DelicateCoroutinesApi::class)
    val selectorManager: SelectorManager by lazy {
      SelectorManager(dispatcher = newSingleThreadContext("SelectorManager"))
    }
  }
}
