package org.emulinker.kaillera.pico

import java.time.Instant

/** Constants inserted at compile time. */
object CompiledFlags {
  const val DEBUG_BUILD: Boolean = ${isDev}

  const val PROJECT_NAME: String = "${project.name}"

  const val PROJECT_VERSION: String = "${project.version}"

  const val PROJECT_URL: String = "${project.url}"

  val BUILD_DATE: Instant = Instant.ofEpochSecond(${buildTimestampSeconds})

  const val USE_BYTEREADPACKET_INSTEAD_OF_BYTEBUFFER: Boolean = ${useBytereadpacketInsteadOfBytebuffer}
}
