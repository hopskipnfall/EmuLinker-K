package org.emulinker.kaillera.pico

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.graphite.Graphite
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import com.codahale.metrics.jvm.ThreadStatesGaugeSet
import com.google.common.flogger.FluentLogger
import java.net.InetSocketAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

private val logger = FluentLogger.forEnclosingClass()

/**
 * metrics Main entry point for the EmuLinker Kaillera server. This method accepts no arguments. It
 * starts the pico container which reads its configuration from components.xml. The server
 * components, once started, read their configuration information from emulinker.xml. Each of those
 * files will be located by using the classpath.
 */
fun main(args: Array<String>) {
  System.setProperty(
      "flogger.backend_factory",
      "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance")
  val component = DaggerAppComponent.create()
  logger.atInfo().log("EmuLinker server Starting...")
  logger.atInfo().log(component.releaseInfo.welcome)
  logger
      .atInfo()
      .log(
          "EmuLinker server is running @ ${DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.now())}")
  component.accessManager.start()
  component.kailleraServerController.start()
  component.server.start()
  component.kailleraServer.start()
  component.masterListUpdater.start()
  val metrics = component.metricRegistry
  metrics.registerAll(ThreadStatesGaugeSet())
  metrics.registerAll(MemoryUsageGaugeSet())
  val flags = component.runtimeFlags
  if (flags.metricsEnabled) {
    // TODO(nue): Pass this data to a central server so we can see how performance changes over
    // time in prod.
    // "graphite" is the name of a service in docker-compose.yaml.
    val graphite = Graphite(InetSocketAddress("graphite", 2003))
    val reporter =
        GraphiteReporter.forRegistry(metrics)
            .convertRatesTo(SECONDS)
            .convertDurationsTo(MILLISECONDS)
            .filter(MetricFilter.ALL)
            .build(graphite)
    reporter.start(30, SECONDS)
  }

  // Hacky code but it works!
  // TODO(nue): Move this into a test file in a subsequent PR.

  //  runBlocking {
  //    delay(10.seconds)
  //
  //    launch {
  //      EvalClient("testuser1", io.ktor.network.sockets.InetSocketAddress("127.0.0.1", 27888)).use
  // {
  //        it.connectToDedicatedPort()
  //        it.start()
  //        delay(1.seconds)
  //        it.createGame()
  //
  //        delay(20.seconds)
  //        logger.atInfo().log("Shutting down everything else")
  //        component.accessManager.stop()
  //        component.kailleraServerController.stop()
  //        component.kailleraServer.stop()
  //        component.masterListUpdater.stop()
  //      }
  //    }
  //
  //    launch {
  //      EvalClient("testuser2", io.ktor.network.sockets.InetSocketAddress("127.0.0.1", 27888)).use
  // {
  //        delay(3.seconds)
  //        it.connectToDedicatedPort()
  //        it.start()
  //        delay(1.seconds)
  //        it.joinAnyAvailableGame()
  //      }
  //    }
  //
  //    launch {
  //      EvalClient("testuser3", io.ktor.network.sockets.InetSocketAddress("127.0.0.1", 27888)).use
  // {
  //        delay(3.seconds)
  //        it.connectToDedicatedPort()
  //        it.start()
  //        delay(1.seconds)
  //        it.joinAnyAvailableGame()
  //      }
  //    }
  //
  //    launch {
  //      EvalClient("testuser4", io.ktor.network.sockets.InetSocketAddress("127.0.0.1", 27888)).use
  // {
  //        delay(3.seconds)
  //        it.connectToDedicatedPort()
  //        it.start()
  //        delay(1.seconds)
  //        it.joinAnyAvailableGame()
  //      }
  //    }
  //  }
}
