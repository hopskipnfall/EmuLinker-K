package org.emulinker.kaillera.controller.v086.action

import io.netty.channel.ChannelHandlerContext
import org.emulinker.kaillera.controller.v086.V086ClientHandler
import org.emulinker.kaillera.model.event.UserEvent

interface V086UserEventHandler<in T : UserEvent?> {
  // TODO(nue): @Deprecated("Structure this in a different way")
  override fun toString(): String

  fun handleEvent(event: T, clientHandler: V086ClientHandler,
                  ctx: ChannelHandlerContext)
}
