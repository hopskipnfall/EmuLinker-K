package org.emulinker.kaillera.model

import io.ktor.network.sockets.*

data class UserData(val id: Int, val name: String, val address: InetSocketAddress)
