package org.emulinker.kaillera.pico

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import java.nio.charset.Charset
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import org.apache.commons.configuration.Configuration
import org.emulinker.config.RuntimeFlags
import org.emulinker.config.RuntimeFlags.Companion.loadFromApacheConfiguration
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.access.AccessManager2
import org.emulinker.kaillera.controller.KailleraServerController
import org.emulinker.kaillera.controller.v086.V086Controller
import org.emulinker.kaillera.master.MasterListStatsCollector
import org.emulinker.kaillera.master.StatsCollector
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactoryImpl
import org.emulinker.util.EmuLinkerPropertiesConfig
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder

@Module
abstract class AppModule {
  @Binds abstract fun bindAccessManager(accessManager2: AccessManager2?): AccessManager?

  @Binds
  abstract fun bindAutoFireDetectorFactory(
    autoFireDetectorFactoryImpl: AutoFireDetectorFactoryImpl?
  ): AutoFireDetectorFactory?

  @Binds
  abstract fun bindKailleraServerController(
    v086Controller: V086Controller?
  ): KailleraServerController?

  @Binds
  @IntoSet
  abstract fun bindKailleraServerControllerToSet(
    v086Controller: V086Controller?
  ): KailleraServerController?

  @Binds
  abstract fun bindStatsCollector(
    masterListStatsCollector: MasterListStatsCollector?
  ): StatsCollector?

  companion object {
    // TODO(nue): Burn this with fire!!!
    // NOTE: This is NOT marked final and there are race conditions involved. Inject @RuntimeFlags
    // instead!
    lateinit var charsetDoNotUse: Charset

    // TODO(nue): Clean this up.
    /**
     * A message to be shown to admins as they log in.
     *
     * Usually used for update messages.
     */
    var updateMessageForMods: String? = null

    @Provides
    @Singleton
    @Named("listeningOnPortsCounter")
    fun bindPortListenerCounter(metrics: MetricRegistry): Counter =
      metrics.counter("listeningOnPorts")

    @Provides fun provideTwitter(twitterFactory: TwitterFactory): Twitter = twitterFactory.instance

    @Provides
    @Singleton
    fun provideTwitterFactory(flags: RuntimeFlags) =
      TwitterFactory(
        ConfigurationBuilder()
          .setDebugEnabled(true)
          .setOAuthAccessToken(flags.twitterOAuthAccessToken)
          .setOAuthAccessTokenSecret(flags.twitterOAuthAccessTokenSecret)
          .setOAuthConsumerKey(flags.twitterOAuthConsumerKey)
          .setOAuthConsumerSecret(flags.twitterOAuthConsumerSecret)
          .build()
      )

    @Provides
    @Singleton
    fun provideConfiguration(): Configuration {
      return EmuLinkerPropertiesConfig()
    }

    @Provides
    @Singleton
    fun provideRuntimeFlags(configuration: Configuration?): RuntimeFlags {
      val flags = loadFromApacheConfiguration(configuration!!)
      charsetDoNotUse = flags.charset
      return flags
    }

    @Provides
    fun provideThreadPoolExecutor(flags: RuntimeFlags): ThreadPoolExecutor {
      return ThreadPoolExecutor(
        flags.coreThreadPoolSize,
        Int.MAX_VALUE,
        60L,
        TimeUnit.SECONDS,
        SynchronousQueue()
      )
    }

    @Provides
    @Singleton
    fun provideMetricRegistry(): MetricRegistry {
      return MetricRegistry()
    }
  }
}
