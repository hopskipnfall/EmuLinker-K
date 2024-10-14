package org.emulinker.kaillera.controller.v086.action

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val ActionModule = module {
  singleOf(::ACKAction)
  singleOf(::AdminCommandAction)
  singleOf(::ChatAction)
  singleOf(::CloseGameAction)
  singleOf(::CreateGameAction)
  singleOf(::DropGameAction)
  singleOf(::GameChatAction)
  singleOf(::GameDesynchAction)
  singleOf(::GameInfoAction)
  singleOf(::GameKickAction)
  singleOf(::GameOwnerCommandAction)
  singleOf(::GameStatusAction)
  singleOf(::GameTimeoutAction)
  singleOf(::InfoMessageAction)
  singleOf(::JoinGameAction)
  singleOf(::KeepAliveAction)
  singleOf(::LoginAction)
  singleOf(::PlayerDesynchAction)
  singleOf(::QuitAction)
  singleOf(::QuitGameAction)
  singleOf(::StartGameAction)
  singleOf(::UserReadyAction)
}
