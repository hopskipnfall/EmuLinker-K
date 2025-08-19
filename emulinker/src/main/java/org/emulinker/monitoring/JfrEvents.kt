package org.emulinker.monitoring

import jdk.jfr.Description
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name

sealed class JfrEvent : Event() {

  inline fun <T> record(block: () -> T): T {
    begin()
    return try {
      block()
    } finally {
      commit()
    }
  }

  @Name("User Login")
  @Description("A user joined the server")
  class UserLogin(
    @field:Label("User ID") var userId: Int,
    @field:Label("User IP Address") val ip: String,
  ) : JfrEvent() {

    @Label("Username") var username: String = ""

    @Label("Successful") var successful: Boolean = false
  }

  @Name("Server Lists Check-in")
  @Description("Reported server status for public game lists")
  class MasterListCheckin : JfrEvent()

  @Name("ELK Check-in")
  @Description("Checked in with central EmuLinker-K server")
  class ElkCheckin : JfrEvent()

  @Name("Create Game")
  @Description("A user created a new game in the server")
  class CreateGame : JfrEvent()

  @Name("Stop Game")
  @Description("A user created a new game in the server")
  class StopGame : JfrEvent()

  class DropGame : JfrEvent()

  @Name("Game Start") class GameStart(@field:Label("Game ID") var gameId: Int) : JfrEvent()

  class QuitServer : JfrEvent()

  class GameChat : JfrEvent()

  class ServerChat : JfrEvent()
}
