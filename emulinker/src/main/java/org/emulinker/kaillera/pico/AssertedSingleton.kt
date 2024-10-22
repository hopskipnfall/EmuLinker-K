package org.emulinker.kaillera.pico

import java.util.concurrent.ConcurrentHashMap

/** Marks a class as @Singleton. */
abstract class AssertedSingleton {
  init {
    val name = this::class.qualifiedName!!
    instances[name] = instances.getOrDefault(name, defaultValue = 0) + 1
    check(instances[name]!! <= 1) {
      "Multiple instances found of singleton $name (${instances[name]})"
    }
  }

  companion object {
    private val instances = ConcurrentHashMap<String, Int>()
  }
}
