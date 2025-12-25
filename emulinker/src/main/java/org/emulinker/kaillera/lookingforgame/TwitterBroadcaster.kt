package org.emulinker.kaillera.lookingforgame

import com.google.common.flogger.FluentLogger
import io.github.redouane59.twitter.TwitterClient
import io.github.redouane59.twitter.dto.tweet.Tweet
import io.github.redouane59.twitter.dto.tweet.TweetParameters
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ScheduledFuture
import kotlin.time.Duration.Companion.seconds
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.EmuLang.getStringOrNull
import org.emulinker.util.TaskScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Observes when a user is looking for a game opponent and publishes a report to one or more
 * external services (e.g. Twitter, Discord).
 */
class TwitterBroadcaster(
  private val flags: RuntimeFlags,
  private val taskScheduler: TaskScheduler,
) : KoinComponent {
  private val twitter: TwitterClient? = if (flags.twitterEnabled) get<TwitterClient>() else null

  private val pendingReports: ConcurrentMap<LookingForGameEvent, ScheduledFuture<*>> =
    ConcurrentHashMap()
  private val postedTweets: ConcurrentMap<LookingForGameEvent, String> = ConcurrentHashMap()

  private var lastTweetContent: String = ""

  /**
   * After the number of seconds defined in the config, it will report.
   *
   * @return Whether or not the timer was started.
   */
  fun reportAndStartTimer(lookingForGameEvent: LookingForGameEvent): Boolean {
    if (twitter == null) {
      return false
    }
    val username: String = lookingForGameEvent.user.name!!
    if (username.contains("＠") || username.contains("@")) {
      val afterAt = username.removePrefix("＠").removePrefix("@")
      if (flags.twitterPreventBroadcastNameSuffixes.any { afterAt.contains(it) }) {
        return false
      }
    }

    // Discard *Chat or *Away "games".
    if (lookingForGameEvent.gameTitle.startsWith("*")) {
      return false
    }

    val user: KailleraUser = lookingForGameEvent.user
    val message =
      "User: ${user.name}\nGame: ${lookingForGameEvent.gameTitle}\nServer: ${flags.serverName} (${flags.serverAddress})"
    if (message == lastTweetContent) {
      // Twitter will not allow us to make the same post twice in a row.
      return false
    }

    val timerTask =
      taskScheduler.schedule(delay = flags.twitterBroadcastDelay) {
        pendingReports.remove(lookingForGameEvent)
        val tweet = twitter.postTweet(message)
        if (tweet.id == null) {
          logger.atWarning().log("Unable to post tweet")
        } else {
          lastTweetContent = message
          user.game!!.announce(getUrl(tweet, twitter.userIdFromAccessToken), user)
          logger.atFine().log("Posted tweet: %s", getUrl(tweet, twitter.userIdFromAccessToken))
          postedTweets[lookingForGameEvent] = tweet.id
        }
      }
    pendingReports[lookingForGameEvent] = timerTask
    return true
  }

  fun cancelActionsForUser(userId: Int): Boolean {
    return cancelMatchingEvents { event: LookingForGameEvent -> event.user.id == userId }
  }

  fun cancelActionsForGame(gameId: Int): Boolean {
    return cancelMatchingEvents { event: LookingForGameEvent -> event.gameId == gameId }
  }

  private fun cancelMatchingEvents(predicate: (LookingForGameEvent) -> Boolean): Boolean {
    if (twitter == null) {
      return false
    }
    val anyModified =
      pendingReports.keys.asSequence().filter(predicate).any { event: LookingForGameEvent ->
        val timerTask = pendingReports[event]
        if (timerTask != null) {
          try {
            timerTask.cancel(/* mayInterruptIfRunning= */ false)
          } catch (e: Exception) {
            // Throws exceptions if already closed and there's no way to check if it's already been
            // closed..
          }
          pendingReports.remove(event)
        }
        true
      }
    val tweetsClosed =
      postedTweets.keys.asSequence().filter(predicate).any { event: LookingForGameEvent ->
        val tweetId = postedTweets[event]
        if (tweetId != null) {
          postedTweets.remove(event)

          taskScheduler.schedule(delay = 0.seconds) {
            if (flags.twitterDeletePostOnClose) {
              twitter.deleteTweet(tweetId)
              logger.atFine().log("Deleted tweet %s", tweetId)
            } else {
              val tweet =
                twitter.postTweet(
                  TweetParameters.builder()
                    .reply(TweetParameters.Reply.builder().inReplyToTweetId(tweetId).build())
                    .text(getStringOrNull("KailleraServerImpl.TweetCloseMessage") ?: "〆")
                    .build()
                )
              logger
                .atFine()
                .log("Posted tweet canceling LFG: %s", getUrl(tweet, twitter.userIdFromAccessToken))
            }
          }
        }
        true
      }
    return anyModified || tweetsClosed
  }

  private companion object {
    val logger = FluentLogger.forEnclosingClass()

    fun getUrl(tweet: Tweet, userId: String) = "https://twitter.com/$userId/status/${tweet.id}"
  }
}
