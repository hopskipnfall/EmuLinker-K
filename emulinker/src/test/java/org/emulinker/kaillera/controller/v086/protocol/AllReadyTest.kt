package org.emulinker.kaillera.controller.v086.protocol

class AllReadyTest : V086MessageTest<AllReady>() {
  override val message = AllReady(42)
  override val byteString = "00"
  override val serializer = AllReady.AllReadySerializer
}
