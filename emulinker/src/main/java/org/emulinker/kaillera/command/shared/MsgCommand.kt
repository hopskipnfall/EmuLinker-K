package org.emulinker.kaillera.command.shared

import java.util.Scanner
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.command.CommandContext
import org.emulinker.kaillera.command.CommandExecutionContext
import org.emulinker.kaillera.command.ServerCommand

/**
 * `/msg <UserID> <message>` — send a private message.
 *
 * Also handles the bare `/msg` shorthand which re-uses the last recipient.
 */
object MsgCommand : ServerCommand {
  override val name = "/msg"
  override val usage = "/msg <UserID> <message>"
  override val description = "Send a private message to a user. Omit UserID to reply to last."
  override val contexts = setOf(CommandContext.SERVER_LOBBY, CommandContext.GAME_CHAT)

  override fun execute(args: String, ctx: CommandExecutionContext) {
    val accessManager = ctx.server.accessManager
    if (
      accessManager.getAccess(ctx.user.socketAddress!!.address) < AccessManager.ACCESS_SUPERADMIN &&
        accessManager.isSilenced(ctx.user.socketAddress!!.address)
    ) {
      reply(ctx, "You are silenced!")
      return
    }

    val scanner = Scanner(args).useDelimiter(" ")
    try {
      scanner.next() // consume "/msg"
      val userID = scanner.nextInt()
      sendMsg(ctx, userID, scanner)
    } catch (e: NoSuchElementException) {
      // No UserID provided — try last contact.
      val lastId = ctx.user.lastMsgID
      if (lastId != -1) {
        sendMsg(ctx, lastId, scanner)
      } else {
        reply(ctx, "Private Message Error: /msg <UserID> <message>")
      }
    }
  }

  private fun sendMsg(ctx: CommandExecutionContext, userID: Int, scanner: Scanner) {
    val target = ctx.server.getUser(userID)
    val sb = StringBuilder()
    while (scanner.hasNext()) sb.append(scanner.next()).append(" ")
    val m = sb.toString().trim()

    if (target == null) {
      reply(ctx, "User Not Found!")
      return
    }
    if (target === ctx.user) {
      reply(ctx, "You can't private message yourself!")
      return
    }
    if (ctx.currentContext == CommandContext.GAME_CHAT && target.game != ctx.user.game) {
      reply(ctx, "User not in this game!")
      return
    }
    if (
      !target.isAcceptingDirectMessages ||
        target.searchIgnoredUsers(ctx.user.connectSocketAddress.address.hostAddress)
    ) {
      reply(ctx, "<${target.name}> Is not accepting private messages!")
      return
    }
    if (m.isBlank() || m.startsWith("\ufffd")) return

    val access = ctx.server.accessManager.getAccess(ctx.user.socketAddress!!.address)
    if (access == AccessManager.ACCESS_NORMAL) {
      if (m.any { it.code < 32 }) {
        reply(ctx, "Private Message Denied: Illegal characters in message")
        return
      }
      if (m.length > 320) {
        reply(ctx, "Private Message Denied: Message Too Long")
        return
      }
    }

    ctx.user.lastMsgID = target.id
    target.lastMsgID = ctx.user.id

    val toLine = "TO: <${target.name}>(${target.id}) <${ctx.user.name}> (${ctx.user.id}): $m"
    val fromLine = "<${ctx.user.name}> (${ctx.user.id}): $m"
    when (ctx.currentContext) {
      CommandContext.SERVER_LOBBY -> {
        ctx.server.announce(toLine, false, ctx.user)
        target.server.announce(fromLine, false, target)
      }
      CommandContext.GAME_CHAT,
      CommandContext.GAME_OWNER -> {
        ctx.user.game?.announce(toLine, ctx.user)
        target.game?.announce(fromLine, target)
      }
    }
  }

  private fun reply(ctx: CommandExecutionContext, msg: String) {
    when (ctx.currentContext) {
      CommandContext.SERVER_LOBBY -> ctx.sendInfo(msg)
      else -> ctx.announceGame(msg, ctx.user)
    }
  }
}
