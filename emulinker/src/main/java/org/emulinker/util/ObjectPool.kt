package org.emulinker.util

import java.util.concurrent.ConcurrentLinkedQueue

/** A threadsafe, fast object pool backed by a [ConcurrentLinkedQueue]. */
class ObjectPool<V>(initialSize: Int = 0, private val allocator: () -> V) {
  private val pool = ConcurrentLinkedQueue<V>()

  init {
    repeat(initialSize) { pool.offer(allocator()) }
  }

  /** Claims an object from the pool. If the pool is empty, a new one is allocated. */
  fun claim(): V = pool.poll() ?: allocator()

  /** Returns an object to the pool. */
  fun recycle(v: V) {
    pool.offer(v)
  }
}
