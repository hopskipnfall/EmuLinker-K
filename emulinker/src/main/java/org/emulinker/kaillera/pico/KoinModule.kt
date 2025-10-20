package org.emulinker.kaillera.pico

import com.codahale.metrics.MetricRegistry
import io.github.redouane59.twitter.TwitterClient
import io.github.redouane59.twitter.signature.TwitterCredentials
import java.nio.charset.Charset
import java.util.Locale
import java.util.Timer
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.access.AccessManager2
import org.emulinker.kaillera.controller.CombinedKailleraController
import org.emulinker.kaillera.controller.KailleraServerController
import org.emulinker.kaillera.controller.v086.V086Controller
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.emulinker.kaillera.master.MasterListStatsCollector
import org.emulinker.kaillera.master.PublicServerInformation
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.master.client.EmuLinkerMasterUpdateTask
import org.emulinker.kaillera.master.client.KailleraMasterUpdateTask
import org.emulinker.kaillera.master.client.MasterListUpdateTask
import org.emulinker.kaillera.master.client.MasterListUpdater
import org.emulinker.kaillera.master.client.ServerCheckinTask
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactoryImpl
import org.emulinker.kaillera.release.ReleaseInfo
import org.emulinker.util.CustomUserStrings
import org.emulinker.util.EmuLinkerPropertiesConfig
import org.emulinker.util.TaskScheduler
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val koinModule = module {
  singleOf(::AccessManager2).bind<AccessManager>()
  singleOf(::V086Controller).bind<KailleraServerController>()
  singleOf(::TaskScheduler)
  singleOf(::MasterListUpdater)
  singleOf(::V086Controller).bind<KailleraServerController>()
  singleOf(::MasterListStatsCollector).bind<StatsCollector>()
  singleOf(::EmuLinkerPropertiesConfig).bind<Configuration>()
  singleOf(::MetricRegistry)
  singleOf(::KailleraServer)
  singleOf(::TwitterBroadcaster)
  singleOf(::ReleaseInfo)
  singleOf(::PublicServerInformation)

  factoryOf(::EmuLinkerMasterUpdateTask).bind<MasterListUpdateTask>()
  factoryOf(::CombinedKailleraController)
  factoryOf(::KailleraMasterUpdateTask).bind<MasterListUpdateTask>()
  factoryOf(::ServerCheckinTask)

  single(named("joinGameMessages")) {
    buildList {
      var i = 1
      while (CustomUserStrings.hasString("KailleraServerImpl.JoinGameMessage.$i")) {
        add(CustomUserStrings.getString("KailleraServerImpl.JoinGameMessage.$i"))
        i++
      }
    }
  }

  single { Clock.System }.bind<Clock>()

  single<ThreadPoolExecutor>(named("userActionsExecutor")) {
    ThreadPoolExecutor(
      get<RuntimeFlags>().coreThreadPoolSize,
      /* maximumPoolSize= */ Integer.MAX_VALUE,
      /* keepAliveTime= */ 60L,
      TimeUnit.SECONDS,
      SynchronousQueue(),
    )
  }

  single { Timer(/* isDaemon= */ true) }

  single<RuntimeFlags> {
    val config = get<Configuration>()
    val flags =
      RuntimeFlags(
        allowMultipleConnections = config.getBoolean("server.allowMultipleConnections", true),
        allowSinglePlayer = config.getBoolean("server.allowSinglePlayer", true),
        charset = Charset.forName(config.getString("emulinker.charset")),
        chatFloodTime = config.getInt("server.chatFloodTime", 2).seconds,
        allowedProtocols =
          config.getStringArray("controllers.v086.clientTypes.clientType").toList(),
        allowedConnectionTypes =
          config
            .getStringArray("server.allowedConnectionTypes")
            .ifEmpty {
              ConnectionType.entries.map { it.byteValue.toInt().toString() }.toTypedArray()
            }
            .toList(),
        coreThreadPoolSize = config.getInt("server.coreThreadpoolSize", 5),
        createGameFloodTime = config.getInt("server.createGameFloodTime", 2).seconds,
        gameAutoFireSensitivity = config.getInt("game.defaultAutoFireSensitivity", 0),
        gameBufferSize = config.getInt("game.bufferSize"),
        idleTimeout = config.getInt("server.idleTimeout", 1.hours.inWholeSeconds.toInt()).seconds,
        keepAliveTimeout = config.getInt("server.keepAliveTimeout", 190).seconds,
        lagstatDuration =
          config.getInt("server.lagstatDurationSeconds", 1.minutes.inWholeSeconds.toInt()).seconds,
        locale =
          when (val locale = config.getString("server.locale", "").lowercase().trim()) {
            "" -> Locale.getDefault()
            else -> Locale.forLanguageTag(locale)
          },
        maxChatLength = config.getInt("server.maxChatLength", 150),
        maxClientNameLength = config.getInt("server.maxClientNameLength", 127),
        maxGameChatLength = config.getInt("server.maxGameChatLength", 320),
        maxGameNameLength = config.getInt("server.maxGameNameLength", 127),
        maxGames = config.getInt("server.maxGames", 0),
        maxPing = config.getInt("server.maxPing").milliseconds,
        maxQuitMessageLength = config.getInt("server.maxQuitMessageLength", 100),
        maxUserNameLength = config.getInt("server.maxUserNameLength", 30),
        maxUsers = config.getInt("server.maxUsers", 0),
        metricsEnabled = config.getBoolean("metrics.enabled", false),
        metricsLoggingFrequency = config.getInt("metrics.loggingFrequencySeconds", 30).seconds,
        nettyFlags = config.getInt("server.nettyThreadpoolSize", 30),
        serverAddress = config.getString("masterList.serverConnectAddress", ""),
        serverLocation = config.getString("masterList.serverLocation", "Unknown"),
        serverName = config.getString("masterList.serverName", "New ELK Server"),
        serverPort = config.getInt("controllers.connect.port", 27888),
        serverWebsite = config.getString("masterList.serverWebsite", ""),
        switchStatusBytesForBuggyClient =
          config.getBoolean("server.switchStatusBytesForBuggyClient", false),
        touchEmulinker = config.getBoolean("masterList.touchEmulinker", false),
        touchKaillera = config.getBoolean("masterList.touchKaillera", false),
        twitterBroadcastDelay = config.getInt("twitter.broadcastDelaySeconds", 15).seconds,
        twitterDeletePostOnClose = config.getBoolean("twitter.deletePostOnClose", false),
        twitterEnabled = config.getBoolean("twitter.enabled", false),
        twitterOAuthAccessToken = config.getString("twitter.auth.oAuthAccessToken", ""),
        twitterOAuthAccessTokenSecret = config.getString("twitter.auth.oAuthAccessTokenSecret", ""),
        twitterOAuthConsumerKey = config.getString("twitter.auth.oAuthConsumerKey", ""),
        twitterOAuthConsumerSecret = config.getString("twitter.auth.oAuthConsumerSecret", ""),
        twitterPreventBroadcastNameSuffixes =
          config.getStringArray("twitter.preventBroadcastNameSuffixes").toList(),
        v086BufferSize = config.getInt("controllers.v086.bufferSize", 4096),
      )

    AppModule.charsetDoNotUse = flags.charset
    AppModule.locale = flags.locale

    flags
  }

  factoryOf(::AutoFireDetectorFactoryImpl).bind<AutoFireDetectorFactory>()

  single<TwitterClient> {
    val flags = get<RuntimeFlags>()
    TwitterClient(
      TwitterCredentials.builder()
        .accessToken(flags.twitterOAuthAccessToken)
        .accessTokenSecret(flags.twitterOAuthAccessTokenSecret)
        .apiKey(flags.twitterOAuthConsumerKey)
        .apiSecretKey(flags.twitterOAuthConsumerSecret)
        .build()
    )
  }
}
