package org.emulinker.kaillera.controller.v086.action

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameOwnerCommandActionTest {
  @Test
  fun swapOrderMustBeAPermutationOfAllPlayerNumbers() {
    assertThat(GameOwnerCommandAction.isValidSwapOrder("21", 2)).isTrue()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("12", 2)).isTrue()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("312", 3)).isTrue()
  }

  @Test
  fun swapOrderRejectsRepeatedNumbers() {
    // The old check only compared the last digit, so these passed with 3 players.
    assertThat(GameOwnerCommandAction.isValidSwapOrder("112", 3)).isFalse()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("121", 3)).isFalse()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("11", 2)).isFalse()
  }

  @Test
  fun swapOrderRejectsWrongLengthOrOutOfRangeNumbers() {
    assertThat(GameOwnerCommandAction.isValidSwapOrder("1", 2)).isFalse()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("123", 2)).isFalse()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("5", 1)).isFalse()
    assertThat(GameOwnerCommandAction.isValidSwapOrder("304", 3)).isFalse()
  }
}
