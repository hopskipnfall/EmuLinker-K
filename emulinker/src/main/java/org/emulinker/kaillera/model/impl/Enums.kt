package org.emulinker.kaillera.model.impl

enum class UserStatus(val byteValue: Byte, val readableName: String) {
  PLAYING(0, "Playing"),
  IDLE(1, "Idle"),
  CONNECTING(2, "Connecting");

  override fun toString() = readableName

  companion object {
    fun fromByteValue(byteValue: Byte): UserStatus {
      return values().find { it.byteValue == byteValue }
          ?: throw IllegalArgumentException("Invalid byte value: $byteValue")
    }
  }
}

enum class GameStatus(val byteValue: Byte, val readableName: String) {
  WAITING(0, "Waiting"),
  SYNCHRONIZING(1, "Synchronizing"),
  PLAYING(2, "Playing");

  override fun toString() = readableName

  companion object {
    fun fromByteValue(byteValue: Byte): GameStatus {
      return values().find { it.byteValue == byteValue }
          ?: throw IllegalArgumentException("Invalid byte value: $byteValue")
    }
  }
}
