package org.emulinker.kaillera.pico

import com.google.common.flogger.FluentLogger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = FluentLogger.forEnclosingClass()

/**
 * Main entry point for the EmuLinker Kaillera server. This method accepts no arguments. It starts
 * the pico container which reads its configuration from components.xml. The server components, once
 * started, read their configuration information from emulinker.xml. Each of those files will be
 * located by using the classpath.
 */
fun main(): Unit =
    runBlocking {
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

      //  component.accessManager.start() // Almost certainly can be removed.
      launch { component.kailleraServerController.start() } // Apparently cannot be removed.
      launch { component.server.start() }

    //  component.kailleraServer.start() // Almost certainly can be removed.
    //  component.masterListUpdater.start()
    //      val metrics = component.metricRegistry
    //      metrics.registerAll(ThreadStatesGaugeSet())
    //      metrics.registerAll(MemoryUsageGaugeSet())
    //      val flags = component.runtimeFlags
    //      if (flags.metricsEnabled) {
    //        // TODO(nue): Pass this data to a central server so we can see how performance changes
    // over
    //        // time in prod.
    //        // "graphite" is the name of a service in docker-compose.yaml.
    //        val graphite = Graphite(java.net.InetSocketAddress("graphite", 2003))
    //        val reporter =
    //            GraphiteReporter.forRegistry(metrics)
    //                .convertRatesTo(SECONDS)
    //                .convertDurationsTo(MILLISECONDS)
    //                .filter(MetricFilter.ALL)
    //                .build(graphite)
    //        reporter.start(30, SECONDS)
    //      }
    }
