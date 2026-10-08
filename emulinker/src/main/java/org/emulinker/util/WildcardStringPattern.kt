package org.emulinker.util

import java.util.LinkedList
import java.util.StringTokenizer

class WildcardStringPattern(pattern: String) {
  private var equals = false
  private var startsWith = false
  private var endsWith = false
  private var contains = false

  private var startString = ""
  private var endString = ""
  private val containsStrings = LinkedList<String>()

  fun match(s: String): Boolean {
    if (s.isBlank()) return false
    if (equals) return s == startString

    // The start, middle and end parts of the pattern must not overlap each other, so track the
    // region of [s] that is still available to the next part.
    var pos = 0
    var end = s.length
    if (startsWith) {
      if (!s.startsWith(startString)) return false
      pos = startString.length
    }
    if (endsWith) {
      if (!s.endsWith(endString) || s.length - endString.length < pos) return false
      end = s.length - endString.length
    }
    for (part in containsStrings) {
      val idx = s.indexOf(part, pos)
      if (idx == -1 || idx + part.length > end) return false
      pos = idx + part.length
    }
    return true
  }

  override fun toString(): String {
    if (equals) return startString

    // If there is a startString, append "*" to it.
    // (There must be a follow up, or this would be an equals match.)
    // If startString is null, the remainder of the pattern is
    // an endsWith or contains. In either case, it must start
    // with "*".
    var s = startString
    s += "*"
    containsStrings.forEach { s += "$it*" }
    s += endString
    return s
  }

  companion object {

    fun main(args: Array<String>) {
      val test = WildcardStringPattern(args[0])
      for (i in 1 until args.size) {
        val match = test.match(args[i])
        println(args[i] + " = " + match)
      }
    }
  }

  init {
    if (pattern.isEmpty()) {
      // match() function will always return true.
    } else {
      val elements = LinkedList<String>()
      val st = StringTokenizer(pattern, "*", true)
      while (st.hasMoreElements()) {
        elements.add(st.nextToken())
      }
      if (elements.size == 1) {
        val s = elements.first
        if (s != "*") {

          // Real text
          equals = true
          startString = elements.first
        }
      } else {
        // Multiple elements in the pattern
        // Pick off start and end strings.
        // Add remaining items to the list of contains strings.
        if (elements.first != "*") {
          startsWith = true
          startString = elements.first
          elements.removeFirst()
        }
        if (elements.last != "*") {
          endsWith = true
          endString = elements.last
          elements.removeLast()
        }
        for (x in elements) {
          if (x == "*") continue
          containsStrings.add(x)
          contains = true
        }
      }
    }
  }
}
