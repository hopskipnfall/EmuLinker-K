package org.emulinker.kaillera.pico

abstract class AppModule {

  companion object {
    // TODO(nue): Clean this up.
    /**
     * Messages to be shown to admins as they log in.
     *
     * Usually used for update messages.
     */
    var messagesToAdmins: List<String> = emptyList()
  }
}
