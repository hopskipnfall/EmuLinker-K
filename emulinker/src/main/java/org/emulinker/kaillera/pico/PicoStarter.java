package org.emulinker.kaillera.pico;

import java.time.Instant;

public class PicoStarter {

  /**
   * Main entry point for the EmuLinker Kaillera server. This method accepts no arguments. It starts
   * the pico container which reads its configuration from components.xml. The server components,
   * once started, read their configuration information from emulinker.xml. Each of those files will
   * be located by using the classpath.
   */
  public static void main(String args[]) {
    AppComponent component = DaggerAppComponent.create();

    System.out.println("EmuLinker server Starting...");
    System.out.println(component.getReleaseInfo().getWelcome());
    System.out.println("EmuLinker server is running @ " + Instant.now());

    component.getKailleraServerController().start();
    component.getServer().start();

    // try {
    //   try {
    //     // new PicoStarter();

    //   } catch (InvocationTargetException ite) {
    //     throw ite.getCause();
    //   } catch (PicoInvocationTargetInitializationException pitie) {
    //     throw pitie.getCause();
    //   }
    // } catch (NoSuchElementException e) {
    //   log.fatal("EmuLinker server failed to start!");
    //   log.fatal(e);
    //   System.out.println("Failed to start! A required propery is missing: " + e.getMessage());
    //   System.exit(1);
    // } catch (ConfigurationException e) {
    //   log.fatal("EmuLinker server failed to start!");
    //   log.fatal(e);
    //   System.out
    //       .println("Failed to start! A configuration parameter is incorrect: " + e.getMessage());
    //   System.exit(1);
    // } catch (BindException e) {
    //   log.fatal("EmuLinker server failed to start!");
    //   log.fatal(e);
    //   System.out.println("Failed to start! A server is already running: " + e.getMessage());
    //   System.exit(1);
    // } catch (Throwable e) {
    //   log.fatal("EmuLinker server failed to start!");
    //   log.fatal(e);
    //   System.err.println(
    //       "Failed to start! Caught unexpected error, stacktrace follows: " + e.getMessage());
    //   e.printStackTrace(System.err);
    //   System.exit(1);
    // }
  }
}
