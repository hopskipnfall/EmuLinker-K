package org.emulinker.kaillera.controller.v086.action

import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val ActionModule = module {
  singleOf(::ACKAction)
  singleOf(::AdminCommandAction)
  singleOf(::ChatAction)
  singleOf(::CloseGameAction)
  single { CreateGameAction(get(named("joinGameMessages"))) }
  singleOf(::DropGameAction)
  singleOf(::GameChatAction)
  singleOf(::GameDesynchAction)
  singleOf(::GameInfoAction)
  singleOf(::GameKickAction)
  singleOf(::GameOwnerCommandAction)
  singleOf(::GameStatusAction)
  singleOf(::InfoMessageAction)
  single { JoinGameAction(get(named("joinGameMessages"))) }
  singleOf(::KeepAliveAction)
  singleOf(::LoginAction)
  singleOf(::PlayerDesynchAction)
  singleOf(::QuitAction)
  singleOf(::QuitGameAction)
  singleOf(::StartGameAction)
  singleOf(::UserReadyAction)
}
