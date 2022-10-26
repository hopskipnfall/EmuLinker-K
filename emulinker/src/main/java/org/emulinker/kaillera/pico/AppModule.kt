package org.emulinker.kaillera.pico

import com.codahale.metrics.MetricRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import java.nio.charset.Charset
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
import org.emulinker.kaillera.model.KailleraServer
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactory
import org.emulinker.kaillera.model.impl.AutoFireDetectorFactoryImpl
import org.emulinker.kaillera.model.impl.KailleraServerImpl
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

  @Binds abstract fun bindKailleraServer(kailleraServerImpl: KailleraServerImpl?): KailleraServer?

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
    private var charsetLoaded = false
    var charsetDoNotUse: Charset = Charsets.UTF_8
      set(value) {
        charsetLoaded = true
        field = value
      }
      get() {
        assert(charsetLoaded)
        return field
      }

    @Provides
    fun provideTwitter(twitterFactory: TwitterFactory): Twitter {
      return twitterFactory.instance
    }

    @Provides
    @Singleton
    fun provideTwitterFactory(flags: RuntimeFlags): TwitterFactory {
      return TwitterFactory(
        ConfigurationBuilder()
          .setDebugEnabled(true)
          .setOAuthAccessToken(flags.twitterOAuthAccessToken)
          .setOAuthAccessTokenSecret(flags.twitterOAuthAccessTokenSecret)
          .setOAuthConsumerKey(flags.twitterOAuthConsumerKey)
          .setOAuthConsumerSecret(flags.twitterOAuthConsumerSecret)
          .build()
      )
    }

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
    @Singleton
    fun provideMetricRegistry(): MetricRegistry {
      return MetricRegistry()
    }
  }
}
