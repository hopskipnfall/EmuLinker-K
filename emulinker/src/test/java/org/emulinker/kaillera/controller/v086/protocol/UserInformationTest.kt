package org.emulinker.kaillera.controller.v086.protocol

import org.emulinker.kaillera.model.ConnectionType

class UserInformationTest : V086MessageTest<UserInformation>() {
  override val message =
    UserInformation(
      MESSAGE_NUMBER,
      username = "nue",
      clientType = "My Emulator",
      connectionType = ConnectionType.LAN,
    )
  override val byteString = "6E, 75, 65, 00, 4D, 79, 20, 45, 6D, 75, 6C, 61, 74, 6F, 72, 00, 01"
  override val serializer = UserInformation.UserInformationSerializer
}
