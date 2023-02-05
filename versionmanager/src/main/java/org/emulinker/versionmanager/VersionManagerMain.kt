package org.emulinker.versionmanager

import com.google.common.flogger.FluentLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.minutes


private val logger = FluentLogger.forEnclosingClass()

/** Main entry point for the version manager/runner. */
fun main(): Unit = runBlocking {
  System.setProperty(
    "flogger.backend_factory",
    "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance"
  )

  logger.atSevere().log("Running!")

  // Read local file to see what version we are currently using and other state.
  val fakeInternalState =
    object {
      val lastVersion: String? = null
    }

  // Read local config for settings on auto updates, api token, etc.
  val fakeConfig =
    object {
      val channel = "alpha"
      val autoUpdate = true
    }

  // Call API to fetch latest detailed version info.
  data class ServerVersion(val versionId: String, val downloadLink: String, val channel: String, val checksum: String)

  val fakeResponse =
    object {
      val versions =
        arrayOf(
          ServerVersion(
            versionId = "0.6.2",
            downloadLink =
              "https://github.com/hopskipnfall/EmuLinkerSF-netsma/releases/download/0.6.2/EmuLinkerSF-netsma-0.6.2.zip",
            channel = "stable",
            checksum = "",
          ),
          ServerVersion(
            versionId = "0.9.0-alpha",
            downloadLink =
              "https://github.com/hopskipnfall/EmuLinkerSF-netsma/releases/download/0.9.0-alpha/emulinkersf-netsma-0.9.0.jar",
            channel = "alpha",
            checksum = "",
          )
        )
    }

  var jarFile: File? = null

  // If necessary, download a new server binary.
  if (fakeInternalState.lastVersion == null || fakeInternalState.lastVersion != fakeResponse.versions.first { it.channel == fakeConfig.channel }.versionId) {
    val toDownload = fakeResponse.versions.first { it.channel == fakeConfig.channel }

    File("./serverVersions/").mkdirs()

    val myFile = File("./serverVersions/${toDownload.versionId}.jar")
    if (myFile.exists()) {
      logger.atSevere().log("Desired version already exists")
    } else {
      logger.atSevere().log("Downloading $myFile")
//      myFile.mkdirs()
      FileUtils.copyURLToFile(URL(toDownload.downloadLink), myFile, 2.minutes.inWholeMilliseconds.toInt(), 2.minutes.inWholeMilliseconds.toInt())
      logger.atSevere().log("Downloading complete")
    }

    jarFile = myFile
  }

  // Check file integrity.

  // Run server binary.

  // Run a java app in a separate system process
  // Run a java app in a separate system process
  //java -Xms64m -Xmx256m -cp ./conf:./lib/emulinkersf-netsma-0.9.0.jar org.emulinker.kaillera.pico.ServerMainKt
  //java -jar A.jar
  val proc = Runtime.getRuntime().exec("java -Xms64m -Xmx256m -cp ./conf:./serverVersions/0.9.0-alpha.jar")
//  val proc = Runtime.getRuntime().exec("java -jar ./conf:./serverVersions/0.9.0-alpha.jar")
  // Then retreive the process output
  // Then retreive the process output
  val `in` = proc.inputStream
  val err = proc.errorStream

  println("pid is " + proc.pid())

//  println("output: " + `in`.readTextAndClose())
//  println("output2: " + err.readTextAndClose())

//  `in`

  delay(1.minutes)

  println("Is alive: ${proc.isAlive}")
  if (proc.isAlive) {
    proc.destroy()
  }

  val a = proc.onExit().get()

  println("exit value: ${a.exitValue()}")


  println("output: " + `in`.readTextAndClose())
  println("output2: " + err.readTextAndClose())

  logger.atSevere().log("Done?")
}

fun InputStream.readTextAndClose(charset: Charset = Charset.forName("Shift_JIS")): String {
  return this.bufferedReader(charset).use { it.readText() }
}
