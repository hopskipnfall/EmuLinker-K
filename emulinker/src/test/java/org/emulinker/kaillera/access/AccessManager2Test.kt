package org.emulinker.kaillera.access

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.emulinker.config.RuntimeFlags
import org.emulinker.util.TaskScheduler
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccessManager2Test {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var accessFile: File
  private lateinit var accessManager: AccessManager2
  private lateinit var taskScheduler: TaskScheduler

  private val localIp: InetAddress = InetAddress.getByName("127.0.0.1")
  private val remoteIp: InetAddress = InetAddress.getByName("10.0.0.42")
  private val otherIp: InetAddress = InetAddress.getByName("192.168.1.1")

  @Before
  fun setUp() {
    // Start with an empty access file — isAddressAllowed() defaults to `true` when no rule matches.
    accessFile = tempFolder.newFile("access.cfg").also { it.writeText("") }
    taskScheduler = TaskScheduler()
    accessManager = buildAccessManager(accessFile)
  }

  @After
  fun tearDown() {
    accessManager.close()
  }

  /** Constructs an [AccessManager2] pointed at [file] instead of the classpath resource. */
  private fun buildAccessManager(file: File): AccessManager2 {
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

  // ── TemporaryAttribute tests ────────────────────────────────────────────────

  @Test
  fun tempBan_storesIssuerAndReason() {
    val ban = TempBan("10.0.0.42", 10.minutes, issuer = "Admin1", reason = "Cheating")
    assertThat(ban.issuer).isEqualTo("Admin1")
    assertThat(ban.reason).isEqualTo("Cheating")
    assertThat(ban.isExpired).isFalse()
  }

  @Test
  fun tempBan_defaultsIssuerAndReasonToNull() {
    val ban = TempBan("10.0.0.42", 10.minutes)
    assertThat(ban.issuer).isNull()
    assertThat(ban.reason).isNull()
  }

  @Test
  fun silence_storesIssuerAndReason() {
    val silence = Silence("10.0.0.42", 5.minutes, issuer = "Mod1", reason = "Spamming")
    assertThat(silence.issuer).isEqualTo("Mod1")
    assertThat(silence.reason).isEqualTo("Spamming")
    assertThat(silence.isExpired).isFalse()
  }

  @Test
  fun tempBan_isExpiredAfterDuration() {
    val ban = TempBan("10.0.0.42", 1.milliseconds)
    Thread.sleep(10)
    assertThat(ban.isExpired).isTrue()
  }

  @Test
  fun silence_isExpiredAfterDuration() {
    val s = Silence("10.0.0.42", 1.milliseconds)
    Thread.sleep(10)
    assertThat(s.isExpired).isTrue()
  }

  // ── addTempBan tests ────────────────────────────────────────────────────────

  @Test
  fun addTempBan_blocksAddress() {
    assertThat(accessManager.isAddressAllowed(remoteIp)).isTrue()
    accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isAddressAllowed(remoteIp)).isFalse()
  }

  @Test
  fun addTempBan_doesNotBlockOtherAddresses() {
    accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isAddressAllowed(otherIp)).isTrue()
  }

  @Test
  fun addTempBan_storesIssuerAndReasonOnTempBanEntry() {
    accessManager.addTempBan(remoteIp.hostAddress, 10.minutes, issuer = "Admin", reason = "Flood")
    val ban = accessManager.getTempBan(remoteIp)
    assertThat(ban).isNotNull()
    assertThat(ban!!.issuer).isEqualTo("Admin")
    assertThat(ban.reason).isEqualTo("Flood")
  }

  @Test
  fun getTempBan_returnsNullWhenNotBanned() {
    assertThat(accessManager.getTempBan(remoteIp)).isNull()
  }

  @Test
  fun addTempBan_withoutOptionalArgs_stillWorks() {
    accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isAddressAllowed(remoteIp)).isFalse()
    val ban = accessManager.getTempBan(remoteIp)
    assertThat(ban).isNotNull()
    assertThat(ban!!.issuer).isNull()
    assertThat(ban.reason).isNull()
  }

  // ── addSilenced tests ───────────────────────────────────────────────────────

  @Test
  fun addSilenced_silencesAddress() {
    assertThat(accessManager.isSilenced(remoteIp)).isFalse()
    accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isSilenced(remoteIp)).isTrue()
  }

  @Test
  fun addSilenced_doesNotSilenceOtherAddresses() {
    accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isSilenced(otherIp)).isFalse()
  }

  @Test
  fun addSilenced_storesIssuerAndReasonOnSilenceEntry() {
    accessManager.addSilenced(remoteIp.hostAddress, 10.minutes, issuer = "Mod", reason = "Abuse")
    val silence = accessManager.getSilence(remoteIp)
    assertThat(silence).isNotNull()
    assertThat(silence!!.issuer).isEqualTo("Mod")
    assertThat(silence.reason).isEqualTo("Abuse")
  }

  @Test
  fun getSilence_returnsNullWhenNotSilenced() {
    assertThat(accessManager.getSilence(remoteIp)).isNull()
  }

  @Test
  fun addSilenced_withoutOptionalArgs_stillWorks() {
    accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isSilenced(remoteIp)).isTrue()
    val silence = accessManager.getSilence(remoteIp)
    assertThat(silence!!.issuer).isNull()
    assertThat(silence.reason).isNull()
  }

  // ── clearTemp tests ─────────────────────────────────────────────────────────

  @Test
  fun clearTemp_removesTempBan() {
    accessManager.addTempBan(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isAddressAllowed(remoteIp)).isFalse()

    val cleared = accessManager.clearTemp(remoteIp, false)
    assertThat(cleared).isTrue()
    assertThat(accessManager.isAddressAllowed(remoteIp)).isTrue()
  }

  @Test
  fun clearTemp_removesSilence() {
    accessManager.addSilenced(remoteIp.hostAddress, 10.minutes)
    assertThat(accessManager.isSilenced(remoteIp)).isTrue()

    accessManager.clearTemp(remoteIp, false)
    assertThat(accessManager.isSilenced(remoteIp)).isFalse()
  }

  @Test
  fun clearTemp_returnsFalseWhenNothingToClear() {
    assertThat(accessManager.clearTemp(remoteIp, false)).isFalse()
  }

  // ── addPermaBan tests ───────────────────────────────────────────────────────

  @Test
  fun addPermaBan_blocksAddress() {
    assertThat(accessManager.isAddressAllowed(remoteIp)).isTrue()
    accessManager.addPermaBan(remoteIp.hostAddress)
    assertThat(accessManager.isAddressAllowed(remoteIp)).isFalse()
  }

  @Test
  fun addPermaBan_writesIpDenyLineToFile() {
    accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = "Ban evasion")
    val content = accessFile.readText()
    assertThat(content).contains("ipaddress,DENY,${remoteIp.hostAddress}")
  }

  @Test
  fun addPermaBan_writesIssuerCommentToFile() {
    accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin123", reason = null)
    val content = accessFile.readText()
    assertThat(content).contains("# Permanent ban issued by Admin123")
  }

  @Test
  fun addPermaBan_writesReasonCommentToFile_whenProvided() {
    accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = "Cheating")
    val content = accessFile.readText()
    assertThat(content).contains("# Reason: Cheating")
  }

  @Test
  fun addPermaBan_doesNotWriteReasonComment_whenNoReason() {
    accessManager.addPermaBan(remoteIp.hostAddress, issuer = "Admin", reason = null)
    val content = accessFile.readText()
    assertThat(content).doesNotContain("# Reason:")
  }

  @Test
  fun addPermaBan_doesNotBlockOtherAddresses() {
    accessManager.addPermaBan(remoteIp.hostAddress)
    assertThat(accessManager.isAddressAllowed(otherIp)).isTrue()
  }

  // ── addPermaMute tests ──────────────────────────────────────────────────────

  @Test
  fun addPermaMute_silencesAddress() {
    assertThat(accessManager.isSilenced(remoteIp)).isFalse()
    accessManager.addPermaMute(remoteIp.hostAddress)
    assertThat(accessManager.isSilenced(remoteIp)).isTrue()
  }

  @Test
  fun addPermaMute_writesSilenceLineToFile() {
    accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Admin", reason = null)
    val content = accessFile.readText()
    assertThat(content).contains("silence,${remoteIp.hostAddress}")
  }

  @Test
  fun addPermaMute_writesIssuerCommentToFile() {
    accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Mod99", reason = null)
    val content = accessFile.readText()
    assertThat(content).contains("# Permanent silence issued by Mod99")
  }

  @Test
  fun addPermaMute_writesReasonCommentToFile_whenProvided() {
    accessManager.addPermaMute(remoteIp.hostAddress, issuer = "Admin", reason = "Hate speech")
    val content = accessFile.readText()
    assertThat(content).contains("# Reason: Hate speech")
  }

  @Test
  fun addPermaMute_doesNotSilenceOtherAddresses() {
    accessManager.addPermaMute(remoteIp.hostAddress)
    assertThat(accessManager.isSilenced(otherIp)).isFalse()
  }

  // ── Static access config tests ──────────────────────────────────────────────

  @Test
  fun isAddressAllowed_defaultsToTrue() {
    assertThat(accessManager.isAddressAllowed(remoteIp)).isTrue()
  }

  @Test
  fun isAddressAllowed_returnsFalseForDeniedPattern() {
    accessFile.writeText("ipaddress,DENY,${remoteIp.hostAddress}\nipaddress,ALLOW,*\n")
    accessManager.forceReload()
    assertThat(accessManager.isAddressAllowed(remoteIp)).isFalse()
    assertThat(accessManager.isAddressAllowed(otherIp)).isTrue()
  }

  @Test
  fun getAccess_returnsAdminForConfiguredSuperadmin() {
    accessFile.writeText("user,SUPERADMIN,${localIp.hostAddress}\nipaddress,ALLOW,*\n")
    accessManager.forceReload()
    assertThat(accessManager.getAccess(localIp)).isEqualTo(AccessManager.ACCESS_SUPERADMIN)
  }

  @Test
  fun getAccess_returnsNormalForUnknownAddress() {
    assertThat(accessManager.getAccess(remoteIp)).isEqualTo(AccessManager.ACCESS_NORMAL)
  }

  fun AccessManager2.forceReload() {
    val method = AccessManager2::class.java.getDeclaredMethod("loadAccess")
    method.isAccessible = true
    method.invoke(this)
  }

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
      )
  }
}
