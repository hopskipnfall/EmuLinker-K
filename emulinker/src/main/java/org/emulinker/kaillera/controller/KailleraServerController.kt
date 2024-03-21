package org.emulinker.kaillera.controller

import java.net.InetSocketAddress
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.exception.NewConnectionException
import org.emulinker.kaillera.model.exception.ServerFullException

interface KailleraServerController {
  val server: KailleraServer
  val bufferSize: Int
  val version: String?
  val numClients: Int
  val clientTypes: Array<String>

  @Throws(ServerFullException::class, NewConnectionException::class)
  fun newConnection(
    clientSocketAddress: InetSocketAddress,
    protocol: String,
    combinedKailleraController: CombinedKailleraController
  ): V086ClientHandler

  fun start()

  fun stop()
}
