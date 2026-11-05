package org.emulinker.testing

import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.SimpleMessageFormatter
import com.google.common.flogger.backend.system.BackendFactory
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.collections.shouldBeEmpty
import java.lang.RuntimeException
import java.util.logging.Level
import java.util.logging.Level.SEVERE
import java.util.logging.Level.WARNING

/** Stores information about logs that occurred during test execution. */
class LogCollection {
  val loggerToMessage = mutableListOf<Pair<String, String>>()

  fun shouldHaveLoggedMatching(regex: Regex) {
    val matched = loggerToMessage.filter { it.second.matches(regex) }
    if (matched.isNotEmpty()) {
      loggerToMessage.remove(matched.first())
    } else {
      fail("Regex $regex did not match any logs in $loggerToMessage.")
    }
  }
}

// Note: This is invoked by Flogger via reflection.
class TestLoggingBackendFactory : BackendFactory() {
  override fun create(p0: String?) = TestLoggingBackend
}

/** Singleton Flogger logging backend that listens to new logs during the test. */
object TestLoggingBackend : LoggerBackend() {
  val logs = LogCollection()

  override fun getLoggerName(): String = "TestLoggingBackend"

  override fun isLoggable(level: Level): Boolean = level in setOf(SEVERE, WARNING)

  override fun log(logData: LogData) {
    handleError(e = null, logData)
  }

  override fun handleError(e: RuntimeException?, logData: LogData) {
    val message =
      if (logData.templateContext == null) {
        logData.literalArgument.toString()
      } else {
        val sb = StringBuilder()
        SimpleMessageFormatter.format(logData) { _, message, _ -> sb.append(message) }
        sb.toString()
      }
    logs.loggerToMessage.add(logData.loggerName!! to message)
  }
}

/**
 * Enforces that logs of levels [WARNING] and [SEVERE] are explicitly expected in tests: clears
 * recorded [WARNING]/[SEVERE] logs before each leaf test and asserts that no unexpected logs remain
 * afterward.
 */
class LoggingListener : BeforeEachListener, AfterEachListener {
  override suspend fun beforeEach(testCase: TestCase) {
    TestLoggingBackend.logs.loggerToMessage.clear()
  }

  override suspend fun afterEach(testCase: TestCase, result: TestResult) {
    withClue("Found unexpected log messages") {
      TestLoggingBackend.logs.loggerToMessage.shouldBeEmpty()
    }
  }
}
