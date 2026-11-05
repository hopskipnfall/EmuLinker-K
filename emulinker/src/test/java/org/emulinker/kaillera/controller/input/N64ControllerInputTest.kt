package org.emulinker.kaillera.controller.input

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled

class N64ControllerInputTest :
  FunSpec({
    val parser = N64ControllerInputParser()

    test("parse standard mode packet with no inputs") {
      val data =
        Unpooled.buffer(24).apply {
          writeByte(0x10) // Player 1 select
          writeByte(0x20) // Standard mode format
          writeZero(10) // Pad header to 12 bytes
          writeZero(12) // 3 frames of 4-byte actions (all zero)
        }

      val frame = parser.parse(data).shouldNotBeNull()
      assertSoftly(frame) {
        a.shouldBeFalse()
        start.shouldBeFalse()
        joystickX shouldBe 0.toByte()
        joystickY shouldBe 0.toByte()
      }

      data.release()
    }

    test("parse standard mode packet with buttons and joystick") {
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

      val frame = parser.parse(data).shouldNotBeNull()
      assertSoftly(frame) {
        a.shouldBeTrue()
        start.shouldBeTrue()
        b.shouldBeFalse()
        joystickX shouldBe 10.toByte()
        joystickY shouldBe (-20).toByte()
      }

      data.release()
    }

    test("parse RAW mode packet with 8-byte frame size") {
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

      val frame = parser.parse(data).shouldNotBeNull()
      assertSoftly(frame) {
        start.shouldBeTrue()
        joystickX shouldBe 1.toByte()
        joystickY shouldBe (-1).toByte()
      }

      data.release()
    }

    test("parse RAW mode packet with 24-byte frame size") {
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

      val frame = parser.parse(data).shouldNotBeNull()
      assertSoftly(frame) {
        start.shouldBeTrue()
        joystickX shouldBe 5.toByte()
      }

      data.release()
    }

    test("parse invalid short packets") {
      val data1 = Unpooled.buffer(1).apply { writeByte(0x10) }
      parser.parse(data1).shouldBeNull()
      data1.release()

      val data2 =
        Unpooled.buffer(5).apply {
          writeByte(0x10)
          writeByte(0x20)
          writeZero(3)
        }
      parser.parse(data2).shouldBeNull()
      data2.release()
    }

    test("toString formats frame state correctly") {
      val frame =
        N64ControllerFrame(
          buttonsHigh = 0x90, // A (0x80) + START (0x10)
          buttonsLow = 0x18, // R (0x10) + C-Up (0x08)
          joystickX = 15,
          joystickY = -30,
        )
      frame.toString() shouldBe "N64Frame{Buttons:Start+A+R+C-Up, Joy:(15, -30)}"
    }

    test("parse individual high-byte buttons") {
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
        property.get(frame).shouldBeTrue()

        // Verify other buttons are false
        buttons
          .filterKeys { it != mask }
          .values
          .forEach { otherProp -> otherProp.get(frame).shouldBeFalse() }
      }
    }

    test("parse individual low-byte buttons") {
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
        property.get(frame).shouldBeTrue()

        // Verify other buttons are false
        buttons
          .filterKeys { it != mask }
          .values
          .forEach { otherProp -> otherProp.get(frame).shouldBeFalse() }
      }
    }

    test("joystick boundary values") {
      val frameMin = N64ControllerFrame(0, 0, -128, -128)
      frameMin.joystickX shouldBe (-128).toByte()
      frameMin.joystickY shouldBe (-128).toByte()

      val frameMax = N64ControllerFrame(0, 0, 127, 127)
      frameMax.joystickX shouldBe 127.toByte()
      frameMax.joystickY shouldBe 127.toByte()
    }

    context("parse packets with partial frame data") {
      withTests(nameFn = { "$it extra bytes" }, 0, 1, 2, 3) { extraBytes ->
        val data =
          Unpooled.buffer(12 + extraBytes).apply {
            writeByte(0x10)
            writeByte(0x20)
            writeZero(10 + extraBytes)
          }
        parser.parse(data).shouldBeNull()
        data.release()
      }
    }

    context("parse rmg-k Little-Endian controller inputs") {
      data class RmgKCase(
        val name: String,
        val bytes: ByteArray,
        val start: Boolean = false,
        val a: Boolean = false,
        val b: Boolean = false,
        val l: Boolean = false,
      )

      withTests(
        nameFn = { it.name },
        RmgKCase("no buttons", byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00)),
        RmgKCase("Start pressed", byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x10), start = true),
        RmgKCase(
          "A + B pressed",
          byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0xC0.toByte()),
          a = true,
          b = true,
        ),
        RmgKCase("A pressed", byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x80.toByte()), a = true),
        RmgKCase("B pressed", byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x40), b = true),
        RmgKCase("L pressed", byteArrayOf(0xFF.toByte(), 0x00, 0x20, 0x00), l = true),
      ) { case ->
        val data =
          Unpooled.buffer(16).apply {
            writeByte(0x10)
            writeByte(0x20)
            writeZero(10)
            writeBytes(case.bytes)
          }
        val frame = parser.parse(data).shouldNotBeNull()
        assertSoftly(frame) {
          start shouldBe case.start
          a shouldBe case.a
          b shouldBe case.b
          l shouldBe case.l
        }
        data.release()
      }
    }
  })
