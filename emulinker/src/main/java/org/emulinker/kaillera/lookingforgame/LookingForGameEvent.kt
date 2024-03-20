package org.emulinker.kaillera.lookingforgame

import org.emulinker.kaillera.model.KailleraUser

data class LookingForGameEvent(
  val gameId: Int,
  val gameTitle: String,
  val user: KailleraUser,
)
