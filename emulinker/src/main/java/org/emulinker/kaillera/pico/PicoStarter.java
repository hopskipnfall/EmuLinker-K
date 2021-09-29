package org.emulinker.kaillera.pico;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.flogger.FluentLogger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PicoStarter {
  public static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Main entry point for the EmuLinker Kaillera server. This method accepts no arguments. It starts
   * the pico container which reads its configuration from components.xml. The server components,
   * once started, read their configuration information from emulinker.xml. Each of those files will
   * be located by using the classpath.
   */
  public static void main(String args[]) {
    System.setProperty(
        "flogger.backend_factory",
        "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance");

    AppComponent component = DaggerAppComponent.create();

    logger.atInfo().log("EmuLinker server Starting...");
    logger.atInfo().log(component.getReleaseInfo().getWelcome());
    logger.atInfo().log(
        "EmuLinker server is running @ "
            + DateTimeFormatter.ISO_ZONED_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(Instant.now()));

    component.getAccessManager().start();
    component.getKailleraServerController().start();
    component.getServer().start();
    component.getKailleraServer().start();
    component.getMasterListUpdaterImpl().start();

    MetricRegistry metrics = component.getMetricRegistry();
    ConsoleReporter reporter =
        ConsoleReporter.forRegistry(metrics)
            .convertRatesTo(SECONDS)
            .convertDurationsTo(MILLISECONDS)
            .build();
    reporter.start(5, SECONDS);

    Meter requests = metrics.meter("requests");
    requests.mark();
    requests.mark();
    requests.mark();
    requests.mark();
    requests.mark();
    requests.mark();
    requests.mark();
    requests.mark();
  }
}
