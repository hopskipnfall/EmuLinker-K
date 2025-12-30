package org.emulinker.kaillera.model

import io.github.hopskipnfall.kaillera.protocol.model.ConnectionType
import io.github.hopskipnfall.kaillera.protocol.model.GameStatus
import io.github.hopskipnfall.kaillera.protocol.model.UserStatus

const val CLIENT_WITH_BYTE_ID_BUG = "Project 64k 0.13 (01 Aug 2003)"

val UserStatus.readableName: String
  get() =
    when (this) {
      UserStatus.PLAYING -> "Playing"
      UserStatus.IDLE -> "Idle"
      UserStatus.CONNECTING -> "Connecting"
    }

fun UserStatus.toValueForBrokenClient(): UserStatus =
  when (this) {
    // CONNECTING and IDLE might be backwards.
    UserStatus.PLAYING -> UserStatus.CONNECTING
    UserStatus.IDLE -> UserStatus.IDLE
    UserStatus.CONNECTING -> UserStatus.PLAYING
  }

val GameStatus.readableName: String
  get() =
    when (this) {
      GameStatus.WAITING -> "Waiting"
      GameStatus.SYNCHRONIZING -> "Synchronizing"
      GameStatus.PLAYING -> "Playing"
    }

fun GameStatus.toValueForBrokenClient(): GameStatus =
  when (this) {
    GameStatus.WAITING -> GameStatus.WAITING
    GameStatus.PLAYING -> GameStatus.SYNCHRONIZING
    GameStatus.SYNCHRONIZING -> GameStatus.PLAYING
  }

val ConnectionType.readableName: String
  get() =
    when (this) {
      ConnectionType.DISABLED -> "DISABLED"
      ConnectionType.LAN -> "LAN"
      ConnectionType.EXCELLENT -> "Excellent"
      ConnectionType.GOOD -> "Good"
      ConnectionType.AVERAGE -> "Average"
      ConnectionType.LOW -> "Low"
      ConnectionType.BAD -> "Bad"
    }

fun ConnectionType.getUpdatesPerSecond(gameFps: Int = KailleraGame.GAME_FPS): Double =
  getUpdatesPerSecond(gameFps.toDouble())

fun ConnectionType.getUpdatesPerSecond(gameFps: Double): Double =
  if (byteValue == 0.toByte()) 0.0 else gameFps / byteValue
