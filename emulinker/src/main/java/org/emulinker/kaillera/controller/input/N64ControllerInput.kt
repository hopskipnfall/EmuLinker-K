package org.emulinker.kaillera.controller.input

import io.netty.buffer.ByteBuf

/** Represents a parsed single frame of N64 controller input. */
class N64ControllerFrame(
  val buttonsHigh: Int,
  val buttonsLow: Int,
  val joystickX: Byte,
  val joystickY: Byte,
) {
  // High Byte buttons
  val a: Boolean
    get() = (buttonsHigh and 0x80) != 0

  val b: Boolean
    get() = (buttonsHigh and 0x40) != 0

  val z: Boolean
    get() = (buttonsHigh and 0x20) != 0

  val start: Boolean
    get() = (buttonsHigh and 0x10) != 0

  val dpadUp: Boolean
    get() = (buttonsHigh and 0x08) != 0

  val dpadDown: Boolean
    get() = (buttonsHigh and 0x04) != 0

  val dpadLeft: Boolean
    get() = (buttonsHigh and 0x02) != 0

  val dpadRight: Boolean
    get() = (buttonsHigh and 0x01) != 0

  // Low Byte buttons
  val l: Boolean
    get() = (buttonsLow and 0x20) != 0

  val r: Boolean
    get() = (buttonsLow and 0x10) != 0

  val cUp: Boolean
    get() = (buttonsLow and 0x08) != 0

  val cDown: Boolean
    get() = (buttonsLow and 0x04) != 0

  val cLeft: Boolean
    get() = (buttonsLow and 0x02) != 0

  val cRight: Boolean
    get() = (buttonsLow and 0x01) != 0

  override fun toString(): String {
    val buttons = buildList {
      if (start) add("Start")
      if (a) add("A")
      if (b) add("B")
      if (z) add("Z")
      if (l) add("L")
      if (r) add("R")
      if (dpadUp) add("D-Up")
      if (dpadDown) add("D-Down")
      if (dpadLeft) add("D-Left")
      if (dpadRight) add("D-Right")
      if (cUp) add("C-Up")
      if (cDown) add("C-Down")
      if (cLeft) add("C-Left")
      if (cRight) add("C-Right")
    }
    val buttonsStr = if (buttons.isNotEmpty()) buttons.joinToString("+") else "None"
    return "N64Frame{Buttons:$buttonsStr, Joy:($joystickX, $joystickY)}"
  }
}

/**
 * Parses Kaillera game data network packets into N64 controller inputs.
 *
 * Supports both standard mode (0x20 format, 12-byte header) and RAW mode (0x21 format, 7-byte
 * header).
 */
class N64ControllerInputParser {
  /**
   * Parses the N64 controller input frame from a Kaillera game data network packet. Returns null if
   * the packet is invalid or too short.
   */
  fun parse(data: ByteBuf): N64ControllerFrame? {
    if (data.readableBytes() < 2) return null

    val formatByte = data.getByte(data.readerIndex() + 1).toInt() and 0xFF
    val isRaw = (formatByte == 0x21)
    val headerSize = if (isRaw) 7 else 12

    if (data.readableBytes() < headerSize + 4) return null

    val offset = data.readerIndex() + headerSize
    if (offset + 3 < data.writerIndex()) {
      val buttonsHigh = data.getByte(offset).toInt() and 0xFF
      val buttonsLow = data.getByte(offset + 1).toInt() and 0xFF
      val joystickX = data.getByte(offset + 2)
      val joystickY = data.getByte(offset + 3)
      return N64ControllerFrame(buttonsHigh, buttonsLow, joystickX, joystickY)
    }
    return null
  }
}
