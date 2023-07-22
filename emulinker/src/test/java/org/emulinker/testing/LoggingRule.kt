package org.emulinker.testing

import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.system.BackendFactory
import com.google.common.truth.Fact.simpleFact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertWithMessage
import java.lang.RuntimeException
import java.util.logging.Level
import java.util.logging.Level.SEVERE
import java.util.logging.Level.WARNING
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Enforces that logs of levels [WARNING] and [SEVERE] are explicitly expected in tests. */
class LoggingRule : TestRule {

  /**
   * Starts a fluent Truth assertion about [WARNING] and [SEVERE]-level logs that happened during
   * the test.
   */
  fun assertThat(): LogCollectionSubject =
    assertAbout(LogCollectionSubject.logCollection()).that(TestLoggingBackend.logs)

  override fun apply(test: Statement, desc: Description) =
    object : Statement() {
      override fun evaluate() {
        TestLoggingBackend.logs.loggerToMessage.clear()

        // Execute the test.
        test.evaluate()

        // Fluent assertions in LogLogSubject subtract from the list of logs as they match.
        assertWithMessage("Found unexpected log messages")
          .that(TestLoggingBackend.logs.loggerToMessage)
          .isEmpty()
      }
    }
}

/** Stores information about logs that occurred during test execution. */
class LogCollection {
  val loggerToMessage = mutableListOf<Pair<String, String>>()
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
    logs.loggerToMessage.add(logData.loggerName to logData.literalArgument.toString())
  }
}

class LogCollectionSubject(failureMetadata: FailureMetadata, private val subject: LogCollection) :
  Subject(failureMetadata, subject) {

  fun messageWasLoggedMatching(regex: Regex) {
    val matched = subject.loggerToMessage.filter { it.second.matches(regex) }
    if (matched.isNotEmpty()) {
      subject.loggerToMessage.remove(matched.first())
    } else {
      failWithActual(simpleFact("Regex did not match any logs."))
    }
  }

  companion object {
    /** Start a Truth assertion with `assertAbout(logCollection())`. */
    fun logCollection() = ::LogCollectionSubject
  }
}
