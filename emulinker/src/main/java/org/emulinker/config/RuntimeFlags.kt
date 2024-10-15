package org.emulinker.config

import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Configuration flags that are set at startup and do not change until the job is restarted. */
data class RuntimeFlags(
  val allowMultipleConnections: Boolean,
  val allowSinglePlayer: Boolean,
  val charset: Charset,
  val chatFloodTime: Int,
  // tbh I have no idea what this does.
  val clientTypes: List<String>,
  val connectionTypes: List<String>,
  val coreThreadPoolSize: Int,
  val createGameFloodTime: Int,
  val gameAutoFireSensitivity: Int,
  val gameBufferSize: Int,
  val gameDesynchTimeouts: Int,
  val gameTimeout: Duration,
  val idleTimeout: Duration,
  val keepAliveTimeout: Duration,
  val lagstatDuration: Duration,
  val maxChatLength: Int,
  val maxClientNameLength: Int,
  val maxGameChatLength: Int,
  val maxGameNameLength: Int,
  val maxGames: Int,
  val maxPing: Duration,
  val maxQuitMessageLength: Int,
  val maxUserNameLength: Int,
  val maxUsers: Int,
  val metricsEnabled: Boolean,
  val metricsLoggingFrequency: Duration,
  val nettyFlags: Int,
  val serverAddress: String,
  val serverLocation: String,
  val serverName: String,
  val serverPort: Int,
  val serverWebsite: String,
  val touchEmulinker: Boolean,
  val touchKaillera: Boolean,
  val twitterBroadcastDelay: Duration,
  val twitterDeletePostOnClose: Boolean,
  val twitterEnabled: Boolean,
  val twitterOAuthAccessToken: String,
  val twitterOAuthAccessTokenSecret: String,
  val twitterOAuthConsumerKey: String,
  val twitterOAuthConsumerSecret: String,
  val twitterPreventBroadcastNameSuffixes: List<String>,
  val v086BufferSize: Int,
) {

  init {
    // Note: this used to be max 30, but for some reason we had 31 set as the default in the config.
    // Setting this to max 31 so we don't break existing users.
    // TODO(nue): Just remove this restriction as it seems unhelpful?
    require(maxUserNameLength <= 31) { "server.maxUserNameLength must be <= 31" }
    require(maxGameNameLength <= 127) { "server.maxGameNameLength must be <= 127" }
    require(maxClientNameLength <= 127) { "server.maxClientNameLength must be <= 127" }
    require(maxPing in 1.milliseconds..1000.milliseconds) { "server.maxPing must be in 1..1000" }
    require(keepAliveTimeout.isPositive()) {
      "server.keepAliveTimeout must be > 0 (190 is recommended)"
    }
    require(lagstatDuration.isPositive()) { "server.lagstatDurationSeconds must be positive" }
    require(gameBufferSize > 0) { "game.bufferSize can not be <= 0" }
    require(gameTimeout.isPositive()) { "game.timeoutMillis can not be <= 0" }
    require(gameAutoFireSensitivity in 0..5) { "game.defaultAutoFireSensitivity must be 0-5" }
    for (s in connectionTypes) {
      try {
        val ct = s.toInt()
        require(ct in 1..6) { "Invalid connectionType: $s" }
      } catch (e: NumberFormatException) {
        throw IllegalStateException("Invalid connectionType: $s", e)
      }
    }
  }
}
