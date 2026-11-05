package org.emulinker.kaillera.access

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import org.emulinker.config.RuntimeFlags
import org.emulinker.util.TaskScheduler

class AccessManager2Test :
  FunSpec({
    lateinit var accessFile: File
    lateinit var accessManager: AccessManager2
    lateinit var taskScheduler: TaskScheduler

    val localIp: InetAddress = InetAddress.getByName("127.0.0.1")
    val remoteIp: InetAddress = InetAddress.getByName("10.0.0.42")
    val otherIp: InetAddress = InetAddress.getByName("192.168.1.1")

    fun AccessManager2.forceReload() {
      val method = AccessManager2::class.java.getDeclaredMethod("loadAccess")
      method.isAccessible = true
      method.invoke(this)
    }

    /** Constructs an [AccessManager2] pointed at [file] instead of the classpath resource. */
    fun buildAccessManager(file: File): AccessManager2 {
      // AccessManager2's `init` block reads from the classpath. We create an instance normally
      // (which loads the real access.cfg from the classpath), then immediately swap `accessFile`
      // and reload so all tests operate against our temp file.
      val instance = AccessManager2(buildFlags(), taskScheduler)
      val field = AccessManager2::class.java.getDeclaredField("accessFile")
      field.isAccessible = true
      field.set(instance, file)
      instance.forceReload()
      return instance
    }

    beforeEach {
      // Start with an empty access file — isAddressAllowed() defaults to `true` when no rule
      // matches.
      accessFile = tempfile("access", ".cfg").also { it.writeText("") }
      taskScheduler = TaskScheduler()
      accessManager = buildAccessManager(accessFile)
    }

    afterEach { accessManager.close() }

    context("TemporaryAttribute") {
      test("TempBan stores issuer and reason") {
        val ban = TempBan("10.0.0.42", 10.minutes, issuer = "Admin1", reason = "Cheating")
        assertSoftly(ban) {
          issuer shouldBe "Admin1"
          reason shouldBe "Cheating"
          isExpired.shouldBeFalse()
        }
      }

      test("TempBan defaults issuer and reason to null") {
        val ban = TempBan("10.0.0.42", 10.minutes)
        ban.issuer.shouldBeNull()
        ban.reason.shouldBeNull()
      }

      test("Silence stores issuer and reason") {
        val silence = Silence("10.0.0.42", 5.minutes, issuer = "Mod1", reason = "Spamming")
        assertSoftly(silence) {
          issuer shouldBe "Mod1"
          reason shouldBe "Spamming"
          isExpired.shouldBeFalse()
        }
      }

      test("TempBan is expired after duration") {
        val ban = TempBan("10.0.0.42", 1.milliseconds)
        delay(10.milliseconds)
        ban.isExpired.shouldBeTrue()
      }

      test("Silence is expired after duration") {
        val s = Silence("10.0.0.42", 1.milliseconds)
        delay(10.milliseconds)
        s.isExpired.shouldBeTrue()
      }
    }

    context("addTempBan") {
      test("blocks address") {
        accessManager.isAddressAllowed(remoteIp).shouldBeTrue()
        accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
      }

      test("does not block other addresses") {
        accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
        accessManager.isAddressAllowed(otherIp).shouldBeTrue()
      }

      test("stores issuer and reason on TempBan entry") {
        accessManager.addTempBan(
          remoteIp.hostAddress,
          10.minutes,
          issuer = "Admin",
          reason = "Flood",
        )
        val ban = accessManager.getTempBan(remoteIp).shouldNotBeNull()
        assertSoftly(ban) {
          issuer shouldBe "Admin"
          reason shouldBe "Flood"
        }
      }

      test("getTempBan returns null when not banned") {
        accessManager.getTempBan(remoteIp).shouldBeNull()
      }

      test("without optional args still works") {
        accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
        val ban = accessManager.getTempBan(remoteIp).shouldNotBeNull()
        ban.issuer.shouldBeNull()
        ban.reason.shouldBeNull()
      }
    }

    context("addSilenced") {
      test("silences address") {
        accessManager.isSilenced(remoteIp).shouldBeFalse()
        accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
        accessManager.isSilenced(remoteIp).shouldBeTrue()
      }

      test("does not silence other addresses") {
        accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
        accessManager.isSilenced(otherIp).shouldBeFalse()
      }

      test("stores issuer and reason on Silence entry") {
        accessManager.addSilenced(
          remoteIp.hostAddress,
          10.minutes,
          issuer = "Mod",
          reason = "Abuse",
        )
        val silence = accessManager.getSilence(remoteIp).shouldNotBeNull()
        assertSoftly(silence) {
          issuer shouldBe "Mod"
          reason shouldBe "Abuse"
        }
      }

      test("getSilence returns null when not silenced") {
        accessManager.getSilence(remoteIp).shouldBeNull()
      }

      test("without optional args still works") {
        accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
        accessManager.isSilenced(remoteIp).shouldBeTrue()
        val silence = accessManager.getSilence(remoteIp).shouldNotBeNull()
        silence.issuer.shouldBeNull()
        silence.reason.shouldBeNull()
      }
    }

    context("clearTemp") {
      test("removes temp ban") {
        accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()

        val cleared = accessManager.clearTemp(remoteIp, false)
        cleared.shouldBeTrue()
        accessManager.isAddressAllowed(remoteIp).shouldBeTrue()
      }

      test("removes silence") {
        accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
        accessManager.isSilenced(remoteIp).shouldBeTrue()

        accessManager.clearTemp(remoteIp, false)
        accessManager.isSilenced(remoteIp).shouldBeFalse()
      }

      test("returns false when nothing to clear") {
        accessManager.clearTemp(remoteIp, false).shouldBeFalse()
      }
    }

    context("addPermaBan") {
      test("blocks address") {
        accessManager.isAddressAllowed(remoteIp).shouldBeTrue()
        accessManager.addPermaBan(remoteIp.hostAddress)
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
      }

      test("writes IP deny line to file") {
        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = "Ban evasion")
        accessFile.readText() shouldContain "ipaddress,DENY,${remoteIp.hostAddress}"
      }

      test("writes issuer comment to file") {
        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin123", reason = null)
        accessFile.readText() shouldContain "# Permanent ban issued by Admin123"
      }

      test("writes reason comment to file when provided") {
        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = "Cheating")
        accessFile.readText() shouldContain "# Reason: Cheating"
      }

      test("does not write reason comment when no reason") {
        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = null)
        accessFile.readText() shouldNotContain "# Reason:"
      }

      test("does not block other addresses") {
        accessManager.addPermaBan(remoteIp.hostAddress)
        accessManager.isAddressAllowed(otherIp).shouldBeTrue()
      }
    }

    context("addPermaMute") {
      test("silences address") {
        accessManager.isSilenced(remoteIp).shouldBeFalse()
        accessManager.addPermaMute(remoteIp.hostAddress)
        accessManager.isSilenced(remoteIp).shouldBeTrue()
      }

      test("writes silence line to file") {
        accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Admin", reason = null)
        accessFile.readText() shouldContain "silence,${remoteIp.hostAddress}"
      }

      test("writes issuer comment to file") {
        accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Mod99", reason = null)
        accessFile.readText() shouldContain "# Permanent silence issued by Mod99"
      }

      test("writes reason comment to file when provided") {
        accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Admin", reason = "Hate speech")
        accessFile.readText() shouldContain "# Reason: Hate speech"
      }

      test("does not silence other addresses") {
        accessManager.addPermaMute(remoteIp.hostAddress)
        accessManager.isSilenced(otherIp).shouldBeFalse()
      }
    }

    context("static access config") {
      test("isAddressAllowed defaults to true") {
        accessManager.isAddressAllowed(remoteIp).shouldBeTrue()
      }

      test("isAddressAllowed returns false for denied pattern") {
        accessFile.writeText("ipaddress,DENY,${remoteIp.hostAddress}\nipaddress,ALLOW,*\n")
        accessManager.forceReload()
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
        accessManager.isAddressAllowed(otherIp).shouldBeTrue()
      }

      test("getAccess returns admin for configured superadmin") {
        accessFile.writeText("user,SUPERADMIN,${localIp.hostAddress}\nipaddress,ALLOW,*\n")
        accessManager.forceReload()
        accessManager.getAccess(localIp) shouldBe AccessManager.ACCESS_SUPERADMIN
      }

      test("addPermaBan newline in reason cannot inject access lines") {
        accessManager.addPermaBan(
          "10.0.0.42",
          issuer = "Evil\nuser,SUPERADMIN,${remoteIp.hostAddress}",
          reason = "x\r\nuser,SUPERADMIN,${remoteIp.hostAddress}",
        )

        accessManager.getAccess(remoteIp) shouldBe AccessManager.ACCESS_NORMAL
        accessFile.readLines().filter { it.startsWith("user,") }.shouldBeEmpty()
      }

      test("addPermaBan comma in pattern cannot change fields or inject lines") {
        accessManager.addPermaBan("10.0.0.42\nuser,SUPERADMIN,1.2.3.4", issuer = "a", reason = null)

        accessFile.readLines().filter { it.startsWith("user,") }.shouldBeEmpty()
      }

      test("malformed line does not prevent later lines from loading") {
        accessFile.writeText(
          "user,NOTALEVEL,${localIp.hostAddress}\nipaddress,DENY,${remoteIp.hostAddress}\n"
        )
        accessManager.forceReload()

        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
      }

      test("addPermaBan keeps CRLF line endings") {
        accessFile.writeBytes("# comment\r\nipaddress,ALLOW,*\r\n".toByteArray())
        accessManager.forceReload()

        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = null)

        val text = accessFile.readText()
        text.replace("\r\n", "") shouldNotContain "\n"
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
      }

      test("addPermaBan adds a newline when the file does not end with one") {
        accessFile.writeText("# comment with no trailing newline")
        accessManager.forceReload()

        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = null)

        accessFile.readLines() shouldContain "# comment with no trailing newline"
        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
      }

      test("addPermaBan leaves the rest of the file byte-for-byte") {
        // 0xFF is not valid UTF-8 and must survive untouched, rewriting must not re-encode the
        // file.
        val before =
          "# caf".toByteArray() + byteArrayOf(0xFF.toByte()) + "\nipaddress,ALLOW,*\n".toByteArray()
        accessFile.writeBytes(before)
        accessManager.forceReload()

        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = null)

        val after = accessFile.readBytes()
        after.copyOfRange(0, 7) shouldBe before.copyOfRange(0, 7)
        after.copyOfRange(after.size - 18, after.size) shouldBe
          before.copyOfRange(before.size - 18, before.size)
      }

      test("sanitizeForConfig replaces control characters and trims") {
        AccessManager2.sanitizeForConfig("a\r\nb\u2028c\n") shouldBe "a  b c"
        // A trailing separator must not leave whitespace that would stop the pattern from matching.
        AccessManager2.sanitizeForConfig("10.0.0.1\n", stripCommas = true) shouldBe "10.0.0.1"
        AccessManager2.sanitizeForConfig("a,b", stripCommas = true) shouldBe "a b"
      }

      test("addPermaBan takes effect despite trailing allow-all rule") {
        // Mirrors the shipped access.cfg: the first matching ipaddress rule wins.
        accessFile.writeText("user,SUPERADMIN,${localIp.hostAddress}\nipaddress,ALLOW,*\n")
        accessManager.forceReload()

        accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = "Cheating")

        accessManager.isAddressAllowed(remoteIp).shouldBeFalse()
        accessManager.isAddressAllowed(otherIp).shouldBeTrue()
        // Existing rules are preserved.
        accessManager.getAccess(localIp) shouldBe AccessManager.ACCESS_SUPERADMIN
      }

      test("temp grant does not lower configured access") {
        accessFile.writeText("user,SUPERADMIN,${localIp.hostAddress}\nipaddress,ALLOW,*\n")
        accessManager.forceReload()

        accessManager.addTempElevated(localIp.hostAddress, 5.minutes)

        accessManager.getAccess(localIp) shouldBe AccessManager.ACCESS_SUPERADMIN
      }

      test("temp grant raises normal access") {
        accessManager.addTempModerator(remoteIp.hostAddress, 5.minutes)

        accessManager.getAccess(remoteIp) shouldBe AccessManager.ACCESS_MODERATOR
      }

      test("getAccess returns normal for unknown address") {
        accessManager.getAccess(remoteIp) shouldBe AccessManager.ACCESS_NORMAL
      }
    }
  }) {
  companion object {
    private fun buildFlags() =
      RuntimeFlags(
        allowMultipleConnections = true,
        allowSinglePlayer = true,
        charset = Charsets.UTF_8,
        chatFloodTime = 0.milliseconds,
        allowedProtocols = listOf("0.83"),
        allowedConnectionTypes = listOf("1"),
        coreThreadPoolSize = 4,
        createGameFloodTime = 0.milliseconds,
        gameAutoFireSensitivity = 0,
        gameBufferSize = 12,
        idleTimeout = 1.minutes,
        keepAliveTimeout = 1.minutes,
        lagstatDuration = 1.minutes,
        language = "en",
        maxChatLength = 256,
        maxClientNameLength = 64,
        maxGameChatLength = 256,
        maxGameNameLength = 64,
        maxGames = 100,
        maxPing = 1000.milliseconds,
        maxQuitMessageLength = 256,
        maxUserNameLength = 31,
        maxUsers = 100,
        metricsEnabled = false,
        metricsLoggingFrequency = 1.minutes,
        serverAddress = "127.0.0.1",
        serverLocation = "Test",
        serverName = "Test Server",
        serverPort = 27888,
        serverWebsite = "",
        switchStatusBytesForBuggyClient = false,
        touchEmulinker = false,
        touchKaillera = false,
        twitterBroadcastDelay = 0.milliseconds,
        twitterDeletePostOnClose = false,
        twitterEnabled = false,
        twitterOAuthAccessToken = "",
        twitterOAuthAccessTokenSecret = "",
        twitterOAuthConsumerKey = "",
        twitterOAuthConsumerSecret = "",
        twitterPreventBroadcastNameSuffixes = emptyList(),
        v086BufferSize = 4096,
        surveyEnabled = false,
        surveyGameWhitelist = emptyList(),
        surveyApiEndpoint = "",
        surveyApiKey = "",
      )
  }
}
