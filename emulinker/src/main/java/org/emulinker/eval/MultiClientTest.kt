package org.emulinker.eval

import io.ktor.network.sockets.*
import org.emulinker.eval.client.EvalClient

fun main() {
  EvalClient(InetSocketAddress("127.0.0.1", 27888))
}
