![Maintained][maintained-badge]
[![build](https://github.com/hopskipnfall/EmuLinker-K/actions/workflows/gradle.yml/badge.svg)](https://github.com/hopskipnfall/EmuLinker-K/actions/workflows/gradle.yml)
[![Crowdin](https://badges.crowdin.net/emulinker-k/localized.svg)](https://crowdin.com/project/emulinker-k)

[![Watch on GitHub][github-watch-badge]][github-watch]
[![Star on GitHub][github-star-badge]][github-star]
[![Tweet][twitter-badge]][twitter]

# EmuLinker-K

EmuLinker-K (ELK) is a server that uses the Kaillera protocol to facilitate online multiplayer for emulators.

EmuLinker-K is a Kotlin rewrite of [EmulinkerSF](https://github.com/God-Weapon/EmuLinkerSF), with an emphasis on
measuring and improving performance, patching security and privacy vulnerabilities, and adding useful features for both
server owners and users.

Please help us translate the server into more languages by contributing to
our [CrowdIn project](https://crowdin.com/project/emulinker-k)

Feel free to file bugs and feature requests on this repository, or find our channel in the Kaillera Reborn discord:

[![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/MqZEph388c)

## Setting up a new server (or updating an existing one)

To install or update the server, simply run the following command in your terminal:

```bash
curl -fsSL https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/setup.sh | bash
```

### Configuration files

#### config/emulinker.cfg

This is the main configuration file for the server.

The most important settings to configure are: `emulinker.language` and `emulinker.charset`.

Here is an exhaustive list of supported options to set in `emulinker.cfg` (most of these you will not need to change):

| Config Key                              | Default Value | Description                                                                                                                                                                                                                                                                                                                                                          |
|-----------------------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| controllers.connect.port                | 27888         | The port used by the server for all communication. Most clients will default to 27888 as the default.                                                                                                                                                                                                                                                                |
| controllers.v086.bufferSize             | 4096          | Buffer size for v086 protocol in bytes.                                                                                                                                                                                                                                                                                                                              |
| controllers.v086.clientTypes.clientType | v086          | Comma-separated list of accepted protocols.                                                                                                                                                                                                                                                                                                                          |
| emulinker.charset                       | (required)    | The character set that matches your language region. Use `Windows-1252` for English and Western Europe, `Windows-1251` for Cyrillic, `GBK` for Simplified Chinese, `Shift_JIS` for Japanese, or `x-IBM949` for Korean. Note: Unicode (`UTF-8`) is generally not supported by clients. If text appears garbled or displays as '?', try selecting a different charset. |
| emulinker.language                      | custom        | Strict ISO 639-1 language code. Use `en` for English, `es` for Spanish, `ja` for Japanese, `ko` for Korean, `pt` for Portuguese, `ru` for Russian, or `zh` for Simplified Chinese, or `custom` if you wish to provide your own translations.                                                                                                                         |
| game.bufferSize                         | (required)    | Buffer size for the game data queue.  Increasing this will not improve performance. Should be equal to at least the largest single game packet.                                                                                                                                                                                                                      |
| game.defaultAutoFireSensitivity         | 0             | Auto-fire sensitivity. 0 (disabled) through 5 (maximum sensitivity)                                                                                                                                                                                                                                                                                                  |
| masterList.serverConnectAddress         |               | Server address for master list registration                                                                                                                                                                                                                                                                                                                          |
| masterList.serverLocation               | Unknown       | Server location for master list display                                                                                                                                                                                                                                                                                                                              |
| masterList.serverName                   | Not Set       | Name used to identify the server                                                                                                                                                                                                                                                                                                                                     |
| masterList.serverWebsite                |               | Server website URL for master list                                                                                                                                                                                                                                                                                                                                   |
| masterList.touchEmulinker               | FALSE         | Registers server with the EmuLinker master list                                                                                                                                                                                                                                                                                                                      |
| masterList.touchKaillera                | FALSE         | Registers server with the Kaillera master list                                                                                                                                                                                                                                                                                                                       |
| metrics.enabled                         | FALSE         | Enables detailed monitoring of server health                                                                                                                                                                                                                                                                                                                         |
| metrics.loggingFrequencySeconds         | 30            | How frequently to log aggregated metrics for debugging                                                                                                                                                                                                                                                                                                               |
| server.allowedConnectionTypes           | 1,2,3,4,5,6   | Comma-separated list of allowed connection types by their ID (1 = LAN, 2 = Excellent, 3 = Good, 4 = Average, 5 = Low, 6 =  Bad)                                                                                                                                                                                                                                      |
| server.allowMultipleConnections         | TRUE          | Controls whether a single IP address can be logged in multiple times at the same time                                                                                                                                                                                                                                                                                |
| server.allowSinglePlayer                | TRUE          | Whether users are allowed to start a game when no other users have joined                                                                                                                                                                                                                                                                                            |
| server.chatFloodTime                    | 2             | Minimimum time (in seconds) a user must wait between sending two chat messages                                                                                                                                                                                                                                                                                       |
| server.coreThreadpoolSize               | 5             | Core size of the thread pool for user actions.                                                                                                                                                                                                                                                                                                                       |
| server.createGameFloodTime              | 2             | Minimum time (in seconds) a user must wait between creating two games                                                                                                                                                                                                                                                                                                |
| server.idleTimeout                      | 3600          | How often a user must engage in the server before being kicked for inactivity (0 to disable)                                                                                                                                                                                                                                                                         |
| server.keepAliveTimeout                 | 190           | Amount of time (in seconds) the server will wait before deciding a client has disconnected. Note: This is not related to user inactivity                                                                                                                                                                                                                             |
| server.lagstatDurationSeconds           | 60            | Duration for lag statistics collection in seconds. This is under active development, please give feedback in the discord instead of overriding this value.                                                                                                                                                                                                           |
| server.maxChatLength                    | 150           | Maximum length of chat messages (0 to disable)                                                                                                                                                                                                                                                                                                                       |
| server.maxClientNameLength              | 127           | Maximum length of the kaillera client name (must be ≤ 127)                                                                                                                                                                                                                                                                                                           |
| server.maxGameChatLength                | 320           | Maximum length of game chat messages                                                                                                                                                                                                                                                                                                                                 |
| server.maxGameNameLength                | 127           | Maximum length of a game name (must be ≤ 127)                                                                                                                                                                                                                                                                                                                        |
| server.maxGames                         | 0             | Maximum number of games that can be created in the server simultaneously (0 to disable)                                                                                                                                                                                                                                                                              |
| server.maxPing                          | 1000          | Maximum allowed roundtrip ping in milliseconds (must be ≤ 1000)                                                                                                                                                                                                                                                                                                      |
| server.maxQuitMessageLength             | 100           | Maximum length of quit messages                                                                                                                                                                                                                                                                                                                                      |
| server.maxUserNameLength                | 30            | Maximum length of user names (must be ≤ 30)                                                                                                                                                                                                                                                                                                                          |
| server.maxUsers                         | 0             | Maximum number of concurrent users logged into the server (0 to disable)                                                                                                                                                                                                                                                                                             |
| server.switchStatusBytesForBuggyClient  | FALSE         | Switch some game/player statuses for a Japanese client "Project 64k 0.13 (01 Aug 2003)" that seems to use different values.                                                                                                                                                                                                                                          |
| twitter.auth.oAuthAccessToken           |               | Twitter OAuth access token                                                                                                                                                                                                                                                                                                                                           |
| twitter.auth.oAuthAccessTokenSecret     |               | Twitter OAuth access token secret                                                                                                                                                                                                                                                                                                                                    |
| twitter.auth.oAuthConsumerKey           |               | Twitter OAuth consumer key                                                                                                                                                                                                                                                                                                                                           |
| twitter.auth.oAuthConsumerSecret        |               | Twitter OAuth consumer secret                                                                                                                                                                                                                                                                                                                                        |
| twitter.broadcastDelaySeconds           | 15            | Delay from starting a game to when the tweet is posted                                                                                                                                                                                                                                                                                                               |
| twitter.deletePostOnClose               | FALSE         | Whether to delete Twitter posts when games close                                                                                                                                                                                                                                                                                                                     |
| twitter.enabled                         | FALSE         | Enable posting to twitter based on game activity                                                                                                                                                                                                                                                                                                                     |
| twitter.preventBroadcastNameSuffixes    |               | Comma-separated list of name suffixes appearing after a @ in the username to prevent posting to twitter                                                                                                                                                                                                                                                              |

#### config/language.properties

This file is used to customize the messages sent by the server to the clients, specifically for when users join the
server or join a game:

```properties
KailleraServerImpl.LoginMessage.1=Welcome to a new EmuLinkerSF-K Server!
KailleraServerImpl.LoginMessage.2=Here is a second message to show to users when they join.
KailleraServerImpl.JoinGameMessage.1=You joined a game!
```

If your server is in Spanish, Korean, Portuguese, Japanese, Russian, or Chinese all other messages have been translated
for you. Please contact us in the discord about adding new translations if you speak a language not represented.

If you choose to use "custom" translations by setting `emulinker.language=custom` in `config/emulinker.cfg`, you will
also use this file to manually
provide translations for everything listed in [messages.properties](emulinker/src/main/i18n/messages.properties).

### Enabling Twitter integration

You can have the server make a Twitter post when a user opens a game, and either delete it or reply marking it as "
closed" when the game starts. At the time of writing this, both options are supported by the Twitter API free tier.

To set it up, configure the following values in `config/emulinker.cfg`:

```
# Twitter reporting integration switch. When enabled it will
# broadcast new open games.
twitter.enabled=true

# Delay (in seconds) before sending a tweet.
twitter.broadcastDelaySeconds=20

# Comma-separated list of phrases that, if found in the name
# after a "@", will prevent tweet posting.
# Example username: nue@waiting
twitter.preventBroadcastNameSuffixes=waiting,restart
# If true, will simply delete the tweet when the game starts.
twitter.deletePostOnClose=false

# You will need to make a new Twitter API app and fill in these values.
twitter.auth.oAuthAccessToken=
twitter.auth.oAuthAccessTokenSecret=
twitter.auth.oAuthConsumerKey=
twitter.auth.oAuthConsumerSecret=
```

With these settings, users whose name ends in @waiting (meaning they are waiting for a specific person to join their
game) or @restart (meaning they are restarting the game and are waiting for the same person to join) will not have
tweets sent. Similarly, users will be notified and given 20 seconds to type `/stop` to stop the tweet from sending.
After the game starts, the account will respond to the original tweet with the text "(opponent found)".

## Contributing

If you're interested in contributing to this project, please read [Contributing.md](CONTRIBUTING.md).

[prs-badge]: https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square

[prs]: http://makeapullrequest.com

[github-watch-badge]: https://img.shields.io/github/watchers/hopskipnfall/EmuLinker-K.svg?style=social

[github-watch]: https://github.com/hopskipnfall/EmuLinker-K/watchers

[github-star-badge]: https://img.shields.io/github/stars/hopskipnfall/EmuLinker-K.svg?style=social

[github-star]: https://github.com/hopskipnfall/EmuLinker-K/stargazers

[twitter]: https://twitter.com/intent/tweet?text=https://github.com/hopskipnfall/EmuLinker-K%20%F0%9F%91%8D

[twitter-badge]: https://img.shields.io/twitter/url/https/github.com/hopskipnfall/EmuLinker-K.svg?style=social

[maintained-badge]: https://img.shields.io/badge/maintained-yes-brightgreen
