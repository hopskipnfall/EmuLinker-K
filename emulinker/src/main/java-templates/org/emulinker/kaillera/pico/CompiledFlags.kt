package org.emulinker.kaillera.pico

import kotlinx.datetime.Instant

/** Constants inserted at compile time. */
object CompiledFlags {
  const val DEBUG_BUILD: Boolean = ${isDev}

  const val PROJECT_NAME: String = "${project.name}"

  const val PROJECT_VERSION: String = "${project.version}"

  const val PROJECT_URL: String = "${project.url}"

  val BUILD_DATE: Instant = Instant.fromEpochSeconds(${buildTimestampSeconds})

  // TODO(nue): Remove this flag.
  const val USE_BYTEBUF_INSTEAD_OF_BYTEBUFFER: Boolean = ${useBytebufInsteadOfBytebuffer}

  /** Indicates a build still in development (and lacking a unique version number). */
  const val PRERELEASE_BUILD: Boolean = ${project.prerelease}
}
