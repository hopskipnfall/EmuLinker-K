package org.emulinker.kaillera.lookingforgame

import com.google.common.flogger.FluentLogger
import io.github.redouane59.twitter.TwitterClient
import io.github.redouane59.twitter.dto.tweet.Tweet
import io.github.redouane59.twitter.dto.tweet.TweetParameters
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.concurrent.schedule
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.EmuLang
import org.emulinker.util.TaskScheduler

/**
 * Observes when a user is looking for a game opponent and publishes a report to one or more
 * external services (e.g. Twitter, Discord).
 */
@Singleton
class TwitterBroadcaster
@Inject
internal constructor(
  private val flags: RuntimeFlags,
  twitterProvider: Provider<TwitterClient>,
  private val taskScheduler: TaskScheduler,
) {
  private val twitter: TwitterClient? = if (flags.twitterEnabled) twitterProvider.get() else null

  private val pendingReports: ConcurrentMap<LookingForGameEvent, TimerTask> = ConcurrentHashMap()
  private val postedTweets: ConcurrentMap<LookingForGameEvent, String> = ConcurrentHashMap()

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

    val timerTask =
      taskScheduler.schedule(delay = flags.twitterBroadcastDelay) {
        pendingReports.remove(lookingForGameEvent)
        val user: KailleraUser = lookingForGameEvent.user
        val message =
          "User: ${user.name}\nGame: ${lookingForGameEvent.gameTitle}\nServer: ${flags.serverName} (${flags.serverAddress})"
        val tweet = twitter.postTweet(message)
        user.game!!.announce(getUrl(tweet, twitter.userIdFromAccessToken), user)
        logger.atFine().log("Posted tweet: %s", getUrl(tweet, twitter.userIdFromAccessToken))
        postedTweets[lookingForGameEvent] = tweet.id
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
            timerTask.cancel()
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
          Observable.just(tweetId).subscribeOn(Schedulers.io()).subscribe { id: String ->
            if (flags.twitterDeletePostOnClose) {
              twitter.deleteTweet(id)
              logger.atFine().log("Deleted tweet %s", id)
            } else {
              val tweet =
                twitter.postTweet(
                  TweetParameters.builder()
                    .reply(TweetParameters.Reply.builder().inReplyToTweetId(id).build())
                    .text(EmuLang.getStringOrDefault("KailleraServerImpl.TweetCloseMessage", "〆"))
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
