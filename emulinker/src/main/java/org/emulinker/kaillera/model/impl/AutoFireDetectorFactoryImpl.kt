package org.emulinker.kaillera.model.impl

import org.emulinker.kaillera.model.KailleraGame

class AutoFireDetectorFactoryImpl : AutoFireDetectorFactory {
  override fun getInstance(game: KailleraGame, defaultSensitivity: Int): AutoFireDetector {
    return AutoFireScanner2(game, defaultSensitivity)
  }
}
