package org.emulinker.kaillera.controller

import com.google.common.flogger.FluentLogger
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

@ChannelHandler.Sharable
class GlobalExceptionHandler : ChannelInboundHandlerAdapter() {
  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.atSevere().withCause(cause).log("Uncaught exception in Netty pipeline")
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
