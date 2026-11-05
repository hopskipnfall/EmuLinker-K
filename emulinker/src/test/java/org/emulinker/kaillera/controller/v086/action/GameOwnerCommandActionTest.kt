package org.emulinker.kaillera.controller.v086.action

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class GameOwnerCommandActionTest :
  FunSpec({
    test("swapOrderMustBeAPermutationOfAllPlayerNumbers") {
      GameOwnerCommandAction.isValidSwapOrder("21", 2).shouldBeTrue()
      GameOwnerCommandAction.isValidSwapOrder("12", 2).shouldBeTrue()
      GameOwnerCommandAction.isValidSwapOrder("312", 3).shouldBeTrue()
    }

    test("swapOrderRejectsRepeatedNumbers") {
      // The old check only compared the last digit, so these passed with 3 players.
      GameOwnerCommandAction.isValidSwapOrder("112", 3).shouldBeFalse()
      GameOwnerCommandAction.isValidSwapOrder("121", 3).shouldBeFalse()
      GameOwnerCommandAction.isValidSwapOrder("11", 2).shouldBeFalse()
    }

    test("swapOrderRejectsWrongLengthOrOutOfRangeNumbers") {
      GameOwnerCommandAction.isValidSwapOrder("1", 2).shouldBeFalse()
      GameOwnerCommandAction.isValidSwapOrder("123", 2).shouldBeFalse()
      GameOwnerCommandAction.isValidSwapOrder("5", 1).shouldBeFalse()
      GameOwnerCommandAction.isValidSwapOrder("304", 3).shouldBeFalse()
    }
  })
