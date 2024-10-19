package org.emulinker.kaillera.pico

import com.google.common.flogger.FluentLogger
import java.util.concurrent.ConcurrentHashMap

/** Marks a class as @Singleton. */
abstract class AssertedSingleton {
  init {
    val name = this::class.simpleName!!
    instances[name] = instances.getOrDefault(name, 0) + 1
    if (instances[name]!! > 1) {
      logger
        .atSevere()
        .log("INVESTIGATE MULTIPLE INSTANCES OF SINGLETON %s ( %d )", name, instances[name])
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
    private val instances = ConcurrentHashMap<String, Int>()
  }
}
