package org.emulinker.kaillera.access

import com.google.common.flogger.FluentLogger
import java.io.File
import java.util.Locale

object BadWordsFilter {
  private val logger = FluentLogger.forEnclosingClass()
  private val badWords = mutableListOf<String>()

  init {
    try {
      val url = BadWordsFilter::class.java.getResource("/badwords.txt")
      val file = if (url != null) File(url.toURI()) else File("conf/badwords.txt")

      if (file.exists() && file.canRead()) {
        file.forEachLine { line ->
          val trimmed = line.trim()
          if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
            badWords.add(trimmed.lowercase(Locale.getDefault()))
          }
        }
        logger.atInfo().log("Loaded %d bad words from badwords.txt", badWords.size)
      } else {
        logger.atWarning().log("Could not find or read badwords.txt")
      }
    } catch (e: Exception) {
      logger.atWarning().withCause(e).log("Failed to load bad words filter")
    }
  }

  fun isMessageInappropriate(message: String): Boolean {
    val lowerMsg = message.lowercase(Locale.getDefault())
    for (word in badWords) {
      if (lowerMsg.contains(word)) { // Simple substring match, could be expanded to word boundaries
        return true
      }
    }
    return false
  }
}
