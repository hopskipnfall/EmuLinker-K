package org.emulinker.kaillera.access

import java.io.Closeable
import java.net.InetAddress
import kotlin.time.Duration

/**
 * An AccessManager is used retrieve, check, and store user access levels and permissions, and game
 * and emulator filters. This interface defines the static access levels and methods that an
 * AccessManager must implement. How the access permissions are stored, checked, and manipulated is
 * left to the implementation class.
 *
 * Most of the main EmuLinker components are passed a handle to the current AccessManager and make
 * calls to it upon user interactions.
 */
interface AccessManager : Closeable {
  /**
   * Checks if address is allowed to connect.
   *
   * @param address IP Address of client
   * @return true if address is allowed to connect
   */
  fun isAddressAllowed(address: InetAddress): Boolean

  /**
   * Checks if address is silenced
   *
   * @param address IP Address of client
   */
  fun isSilenced(address: InetAddress): Boolean

  /**
   * Checks if client's emulator is allowed (not filtered)
   *
   * @param emulator Emulator name of client
   */
  fun isEmulatorAllowed(emulator: String?): Boolean

  /**
   * Checks if client's game (ROM) is allowed (not filtered)
   *
   * @param game Game name of client
   */
  fun isGameAllowed(game: String): Boolean

  /**
   * Returns the client's assigned access level
   *
   * @param address IP Address of client
   * @return The access level or the default access level if not found
   */
  fun getAccess(address: InetAddress): Int

  /**
   * Returns a login announcement string
   *
   * @param address IP Address of client
   * @return The login announcement, null if not defined
   */
  fun getAnnouncement(address: InetAddress): String?

  /**
   * Temporarily adds a user to the banned list using a pattern algorithm defined by the
   * AccessManager implementation. While active, [isAddressAllowed] should return false, and
   * [getAccess] should return [ACCESS_BANNED].
   *
   * @param addressPattern A pattern to match to an address
   * @param minutes Number of minutes this ban is valid from the time of addition
   */
  fun addTempBan(addressPattern: String, duration: Duration)

  /**
   * Temporarily adds a user to the admin list using a pattern algorithm defined by the
   * AccessManager implementation. While active, [getAccess] should return [ACCESS_ADMIN].
   *
   * @param addressPattern A pattern to match to an address
   * @param minutes Number of minutes this grant is valid from the time of addition
   */
  fun addTempAdmin(addressPattern: String, duration: Duration)
  fun addTempModerator(addressPattern: String, duration: Duration)
  fun addTempElevated(addressPattern: String, duration: Duration)

  /**
   * Temporarily adds a user to the silenced list using a pattern algorithm defined by the
   * AccessManager implementation. While active, [isSilenced] should return `true ` * .
   *
   * @param addressPattern A pattern to match to an address
   * @param minutes Number of minutes this grant is valid from the time of addition
   */
  fun addSilenced(addressPattern: String, duration: Duration)
  fun clearTemp(address: InetAddress, clearAll: Boolean): Boolean

  companion object {
    const val ACCESS_BANNED = 0
    const val ACCESS_NORMAL = 1
    const val ACCESS_ELEVATED = 2
    const val ACCESS_MODERATOR = 3
    const val ACCESS_ADMIN = 4
    const val ACCESS_SUPERADMIN = 5

    val ACCESS_NAMES = arrayOf("Banned", "Normal", "Elevated", "Moderator", "Admin", "SuperAdmin")
  }
}
