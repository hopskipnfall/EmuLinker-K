package org.emulinker.util

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.Throws

object HexConverter {
  @Throws(Exception::class)
  fun main(args: Array<String>) {
    val os = BufferedOutputStream(FileOutputStream(args[1]))
    val `is` = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8)
    `is`.forEachLine {
      val bytes = EmuUtil.hexToByteArray(it)
      os.write(bytes)
    }
    `is`.close()
    os.close()
  }
}
