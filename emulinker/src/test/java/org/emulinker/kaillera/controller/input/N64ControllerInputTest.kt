package org.emulinker.kaillera.controller.input

import com.google.common.truth.Truth.assertThat
import io.netty.buffer.Unpooled
import kotlin.test.Test

class N64ControllerInputTest {

  private val parser = N64ControllerInputParser()

  @Test
  fun `parse standard mode packet with no inputs`() {
    val data =
      Unpooled.buffer(24).apply {
        writeByte(0x10) // Player 1 select
        writeByte(0x20) // Standard mode format
        writeZero(10) // Pad header to 12 bytes
        writeZero(12) // 3 frames of 4-byte actions (all zero)
      }

    val frame = parser.parse(data)
    assertThat(frame).isNotNull()
    assertThat(frame!!.a).isFalse()
    assertThat(frame.start).isFalse()
    assertThat(frame.joystickX).isEqualTo(0.toByte())
    assertThat(frame.joystickY).isEqualTo(0.toByte())

    data.release()
  }

  @Test
  fun `parse standard mode packet with buttons and joystick`() {
    val data =
      Unpooled.buffer(24).apply {
        writeByte(0x10)
        writeByte(0x20)
        writeZero(10)

        // Frame 1: A and START pressed, Joystick X = 10, Y = -20
        writeByte(0x90) // A (0x80) + START (0x10)
        writeByte(0x00)
        writeByte(10)
        writeByte(-20)

        writeZero(8) // Remaining frames
      }

    val frame = parser.parse(data)
    assertThat(frame).isNotNull()
    assertThat(frame!!.a).isTrue()
    assertThat(frame.start).isTrue()
    assertThat(frame.b).isFalse()
    assertThat(frame.joystickX).isEqualTo(10.toByte())
    assertThat(frame.joystickY).isEqualTo((-20).toByte())

    data.release()
  }

  @Test
  fun `parse RAW mode packet with 8-byte frame size`() {
    val data =
      Unpooled.buffer(24).apply {
        writeByte(0x10)
        writeByte(0x21) // RAW mode
        writeZero(5) // Pad header to 7 bytes

        // Frame 1: START pressed (index 7), Joystick X = 1, Y = -1
        writeByte(0x10) // buttonsHigh
        writeByte(0x00) // buttonsLow
        writeByte(1)
        writeByte(-1)
        writeZero(4) // Pad frame to 8 bytes

        writeZero(9) // Remaining bytes
      }

    val frame = parser.parse(data)
    assertThat(frame).isNotNull()
    assertThat(frame!!.start).isTrue()
    assertThat(frame.joystickX).isEqualTo(1.toByte())
    assertThat(frame.joystickY).isEqualTo((-1).toByte())

    data.release()
  }

  @Test
  fun `parse RAW mode packet with 24-byte frame size`() {
    val data =
      Unpooled.buffer(24).apply {
        writeByte(0x10)
        writeByte(0x21) // RAW mode
        writeZero(5) // Pad header to 7 bytes

        // Frame 1: START pressed (index 7), Joystick X = 5, Y = 5
        writeByte(0x10)
        writeByte(0x00)
        writeByte(5)
        writeByte(5)
        writeZero(12)
      }

    val frame = parser.parse(data)
    assertThat(frame).isNotNull()
    assertThat(frame!!.start).isTrue()
    assertThat(frame.joystickX).isEqualTo(5.toByte())

    data.release()
  }

  @Test
  fun `parse invalid short packets`() {
    val data1 = Unpooled.buffer(1).apply { writeByte(0x10) }
    val frame1 = parser.parse(data1)
    assertThat(frame1).isNull()
    data1.release()

    val data2 =
      Unpooled.buffer(5).apply {
        writeByte(0x10)
        writeByte(0x20)
        writeZero(3)
      }
    val frame2 = parser.parse(data2)
    assertThat(frame2).isNull()
    data2.release()
  }

  @Test
  fun `toString formats frame state correctly`() {
    val frame =
      N64ControllerFrame(
        buttonsHigh = 0x90, // A (0x80) + START (0x10)
        buttonsLow = 0x18, // R (0x10) + C-Up (0x08)
        joystickX = 15,
        joystickY = -30,
      )
    assertThat(frame.toString()).isEqualTo("N64Frame{Buttons:Start+A+R+C-Up, Joy:(15, -30)}")
  }

  @Test
  fun `parse individual high-byte buttons`() {
    val buttons =
      mapOf(
        0x80 to N64ControllerFrame::a,
        0x40 to N64ControllerFrame::b,
        0x20 to N64ControllerFrame::z,
        0x10 to N64ControllerFrame::start,
        0x08 to N64ControllerFrame::dpadUp,
        0x04 to N64ControllerFrame::dpadDown,
        0x02 to N64ControllerFrame::dpadLeft,
        0x01 to N64ControllerFrame::dpadRight,
      )

    buttons.forEach { (mask, property) ->
      val frame =
        N64ControllerFrame(buttonsHigh = mask, buttonsLow = 0, joystickX = 0, joystickY = 0)
      assertThat(property.get(frame)).isTrue()

      // Verify other buttons are false
      buttons
        .filterKeys { it != mask }
        .values
        .forEach { otherProp ->
          assertThat(otherProp.get(frame)).isFalse()
        }
    }
  }

  @Test
  fun `parse individual low-byte buttons`() {
    val buttons =
      mapOf(
        0x20 to N64ControllerFrame::l,
        0x10 to N64ControllerFrame::r,
        0x08 to N64ControllerFrame::cUp,
        0x04 to N64ControllerFrame::cDown,
        0x02 to N64ControllerFrame::cLeft,
        0x01 to N64ControllerFrame::cRight,
      )

    buttons.forEach { (mask, property) ->
      val frame =
        N64ControllerFrame(buttonsHigh = 0, buttonsLow = mask, joystickX = 0, joystickY = 0)
      assertThat(property.get(frame)).isTrue()

      // Verify other buttons are false
      buttons
        .filterKeys { it != mask }
        .values
        .forEach { otherProp ->
          assertThat(otherProp.get(frame)).isFalse()
        }
    }
  }

  @Test
  fun `joystick boundary values`() {
    val frameMin = N64ControllerFrame(0, 0, -128, -128)
    assertThat(frameMin.joystickX).isEqualTo((-128).toByte())
    assertThat(frameMin.joystickY).isEqualTo((-128).toByte())

    val frameMax = N64ControllerFrame(0, 0, 127, 127)
    assertThat(frameMax.joystickX).isEqualTo(127.toByte())
    assertThat(frameMax.joystickY).isEqualTo(127.toByte())
  }

  @Test
  fun `parse packets with partial frame data`() {
    for (extraBytes in 0..3) {
      val data =
        Unpooled.buffer(12 + extraBytes).apply {
          writeByte(0x10)
          writeByte(0x20)
          writeZero(10 + extraBytes)
        }
      val frame = parser.parse(data)
      assertThat(frame).isNull()
      data.release()
    }
  }
}
