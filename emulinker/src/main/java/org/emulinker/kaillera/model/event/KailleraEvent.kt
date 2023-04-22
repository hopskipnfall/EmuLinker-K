package org.emulinker.kaillera.model.event

sealed interface KailleraEvent {
  override fun toString(): String
}

// TODO(nue): I don't like this..
class StopFlagEvent : KailleraEvent {
  override fun toString(): String {
    return "StopFlagEvent"
  }
}
