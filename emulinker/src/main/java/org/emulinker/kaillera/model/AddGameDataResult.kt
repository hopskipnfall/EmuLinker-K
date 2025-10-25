package org.emulinker.kaillera.model

import org.emulinker.kaillera.model.exception.GameDataException

sealed interface AddDataResult {
  object Success : AddDataResult

  object IgnoringDesynched : AddDataResult

  class Failure(val exception: GameDataException) : AddDataResult
}
