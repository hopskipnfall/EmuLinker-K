package org.emulinker.kaillera.controller.v086.commands

import com.google.common.truth.Truth.assertThat
import org.emulinker.kaillera.controller.v086.protocol.ProtocolBaseTest
import org.emulinker.kaillera.lookingforgame.TwitterBroadcaster
import org.junit.Test
import org.mockito.kotlin.mock

class CommandsTest : ProtocolBaseTest() {
  val twitterBroadcaster = mock<TwitterBroadcaster>()
  val target = GameChatCommandHandler(twitterBroadcaster)

  @Test
  fun allCommandsUnique() {
    assertThat(target.commands.map { it.prefix }).containsNoDuplicates()
  }
}
