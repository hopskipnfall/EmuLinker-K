package org.emulinker.testing

import com.google.common.truth.Fact.simpleFact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertWithMessage
import kotlin.reflect.KClass
import org.apache.logging.log4j.Level.ERROR
import org.apache.logging.log4j.Level.FATAL
import org.apache.logging.log4j.Level.WARN
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Enforces that logs of level [WARN], [ERROR], and [FATAL] are explicitly expected in tests. */
class LoggingRule(private val clazz: KClass<*>, private vararg val moreClazzez: KClass<*>) :
  TestRule {
  private val appender = ListAppender()

  fun assertThatLogs(): LogSubject = assertAbout(LogSubject.logCollection()).that(appender)

  override fun apply(statement: Statement, desc: Description) =
    object : Statement() {
      override fun evaluate() {
        for (c in listOf(clazz, *moreClazzez)) {
          // Cast it as the implementation type so we have access to the addAppender() method.
          val logger = LogManager.getLogger(c.java) as Logger
          logger.addAppender(appender)
        }

        statement.evaluate()

        assertWithMessage("Found unexpected log messages").that(appender.loggerToMessage).isEmpty()
      }
    }
}

class ListAppender :
  AbstractAppender(
    "TestAppender",
    /* filter= */ null,
    /* layout= */ null,
    /* ignoreExceptions= */ true,
    Property.EMPTY_ARRAY
  ) {
  val loggerToMessage = mutableListOf<Pair<String, String>>()

  override fun append(event: LogEvent) {
    if (event.level in ENFORCED_LOGGING_LEVELS) {
      loggerToMessage.add(event.loggerName to event.message.formattedMessage)
    }
  }

  private companion object {
    val ENFORCED_LOGGING_LEVELS = setOf(WARN, ERROR, FATAL)
  }
}

class LogSubject(failureMetadata: FailureMetadata, private val subject: ListAppender) :
  Subject(failureMetadata, subject) {

  fun contrainsEntryMatching(regex: Regex) {
    val matched = subject.loggerToMessage.filter { it.second.matches(regex) }
    if (matched.isNotEmpty()) {
      subject.loggerToMessage.remove(matched.first())
    } else {
      failWithActual(simpleFact("Regex did not match any logs."))
    }
  }

  companion object {
    @JvmStatic
    fun logCollection() =
      Factory<LogSubject, ListAppender> { failureMetadata, subject ->
        LogSubject(failureMetadata, subject)
      }
  }
}
