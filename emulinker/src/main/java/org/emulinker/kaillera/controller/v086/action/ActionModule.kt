package org.emulinker.kaillera.controller.v086.action

import org.emulinker.kaillera.command.CommandRegistry
import org.emulinker.kaillera.command.admin.AdminFindUserCommand
import org.emulinker.kaillera.command.admin.AdminVersionCommand
import org.emulinker.kaillera.command.admin.AnnounceCommand
import org.emulinker.kaillera.command.admin.AnnounceGameCommand
import org.emulinker.kaillera.command.admin.BanCommand
import org.emulinker.kaillera.command.admin.ClearCommand
import org.emulinker.kaillera.command.admin.CloseGameCommand
import org.emulinker.kaillera.command.admin.FindGameCommand
import org.emulinker.kaillera.command.admin.InfoCommand
import org.emulinker.kaillera.command.admin.PermaMuteCommand
import org.emulinker.kaillera.command.admin.PermabanCommand
import org.emulinker.kaillera.command.admin.ServerKickCommand
import org.emulinker.kaillera.command.admin.SilenceCommand
import org.emulinker.kaillera.command.admin.StealthCommand
import org.emulinker.kaillera.command.admin.TempAdminCommand
import org.emulinker.kaillera.command.admin.TempElevatedCommand
import org.emulinker.kaillera.command.admin.TempModeratorCommand
import org.emulinker.kaillera.command.admin.TriviaCommand
import org.emulinker.kaillera.command.game.FpsCommand
import org.emulinker.kaillera.command.game.LagResetCommand
import org.emulinker.kaillera.command.game.LagstatCommand
import org.emulinker.kaillera.command.game.LogGameCommand
import org.emulinker.kaillera.command.game.P2PCommand
import org.emulinker.kaillera.command.game.StopCommand
import org.emulinker.kaillera.command.game_owner.DetectAutoFireCommand
import org.emulinker.kaillera.command.game_owner.GameKickCommand
import org.emulinker.kaillera.command.game_owner.MaxPingCommand
import org.emulinker.kaillera.command.game_owner.MaxUsersCommand
import org.emulinker.kaillera.command.game_owner.MuteCommand
import org.emulinker.kaillera.command.game_owner.NumCommand
import org.emulinker.kaillera.command.game_owner.SameDelayCommand
import org.emulinker.kaillera.command.game_owner.SetConnCommand
import org.emulinker.kaillera.command.game_owner.SetEmuCommand
import org.emulinker.kaillera.command.game_owner.StartCommand
import org.emulinker.kaillera.command.game_owner.StartNCommand
import org.emulinker.kaillera.command.game_owner.SwapCommand
import org.emulinker.kaillera.command.game_owner.UnmuteCommand
import org.emulinker.kaillera.command.shared.AliveCheckCommand
import org.emulinker.kaillera.command.shared.FindUserCommand
import org.emulinker.kaillera.command.shared.HelpCommand
import org.emulinker.kaillera.command.shared.IgnoreAllCommand
import org.emulinker.kaillera.command.shared.IgnoreCommand
import org.emulinker.kaillera.command.shared.MeCommand
import org.emulinker.kaillera.command.shared.MsgCommand
import org.emulinker.kaillera.command.shared.MsgOffCommand
import org.emulinker.kaillera.command.shared.MsgOnCommand
import org.emulinker.kaillera.command.shared.MyIpCommand
import org.emulinker.kaillera.command.shared.UnignoreAllCommand
import org.emulinker.kaillera.command.shared.UnignoreCommand
import org.emulinker.kaillera.command.shared.VersionCommand
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val ActionModule = module {
  // ── Existing action singletons ──────────────────────────────────────────────
  singleOf(::ACKAction)
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
  singleOf(::InfoMessageAction)
  singleOf(::JoinGameAction)
  singleOf(::KeepAliveAction)
  singleOf(::LoginAction)
  singleOf(::PlayerDesynchAction)
  singleOf(::QuitAction)
  singleOf(::QuitGameAction)
  singleOf(::StartGameAction)
  singleOf(::UserReadyAction)

  // ── Commands that require dependencies (class-based) ────────────────────────
  singleOf(::LagstatCommand)
  singleOf(::StopCommand)

  // ── Command registry ─────────────────────────────────────────────────────────
  single<CommandRegistry> {
    CommandRegistry(
      listOf(
        // shared
        HelpCommand,
        MeCommand,
        MsgCommand,
        MsgOnCommand,
        MsgOffCommand,
        IgnoreCommand,
        UnignoreCommand,
        IgnoreAllCommand,
        UnignoreAllCommand,
        MyIpCommand,
        AliveCheckCommand,
        VersionCommand,
        FindUserCommand,
        // admin
        BanCommand,
        SilenceCommand,
        ServerKickCommand,
        PermabanCommand,
        PermaMuteCommand,
        ClearCommand,
        InfoCommand,
        AnnounceCommand,
        AnnounceGameCommand,
        FindGameCommand,
        AdminFindUserCommand,
        CloseGameCommand,
        AdminVersionCommand,
        TempAdminCommand,
        TempModeratorCommand,
        TempElevatedCommand,
        StealthCommand,
        TriviaCommand,
        // game chat (class-based — resolve from Koin)
        P2PCommand,
        get<LagstatCommand>(),
        LagResetCommand,
        FpsCommand,
        get<StopCommand>(),
        LogGameCommand,
        // game owner
        StartCommand,
        StartNCommand,
        GameKickCommand,
        MuteCommand,
        UnmuteCommand,
        SwapCommand,
        MaxUsersCommand,
        MaxPingCommand,
        SetEmuCommand,
        SetConnCommand,
        DetectAutoFireCommand,
        SameDelayCommand,
        NumCommand,
      )
    )
  }
}
