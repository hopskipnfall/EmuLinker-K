![Maintained][maintained-badge]
[![build](https://github.com/hopskipnfall/EmuLinker-K/actions/workflows/gradle.yml/badge.svg)](https://github.com/hopskipnfall/EmuLinker-K/actions/workflows/maven.yml)
[![Make a pull request][prs-badge]][prs]

[![Watch on GitHub][github-watch-badge]][github-watch]
[![Star on GitHub][github-star-badge]][github-star]
[![Tweet][twitter-badge]][twitter]

# EmuLinker-K

EmuLinker-K is a server that uses the Kaillera protocol to facilitate online multiplayer for emulators.

EmuLinker-K is a Kotlin rewrite of [EmulinkerSF](https://github.com/God-Weapon/EmuLinkerSF), with an emphasis on measuring and improving performance, patching security and privacy vulnerabilities, and adding useful features for both server owners and users.  EmuLinker-K is maintained by [nue](https://twitter.com/6kRt62r2zvKp5Rh).

Feel free to file bugs and feature requests on this repository, or find our channel in the Kaillera Reborn discord: 

[![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/MqZEph388c)

## Getting Started

If you want to start a new server, see our [Releases](https://github.com/hopskipnfall/EmuLinker-K/releases/latest) page for the latest stable release.

```kt
// TODO(nue): Write better instructions, including how to work with charsets.
```

## Development

From the root directory, you can perform common tasks with the following commands:

| Command                          | Description                            |
|----------------------------------|----------------------------------------|
| `./gradlew clean`                | Clean build resources.                 |
| `./gradlew compileKotlin`        | Compile the code.                      |
| `./gradlew test`                 | Run unit tests.                        |
| `./gradlew run`                  | Run the server locally.                |
| `./gradlew jar -PprodBuild=true` | Build the jar used for PROD.           |
| `./gradlew spotlessCheck`        | Run the linter.                        |
| `./gradlew spotlessApply`        | Run the formatter.                     |
| `./gradlew tasks`                | See a full list of available commands. |

[Intellij IDEA](https://www.jetbrains.com/idea/download) is recommended for writing code for this repository. 

### Documentation

KDoc documentation is automatically published to https://hopskipnfall.github.io/EmuLinker-K.

[prs-badge]: https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square
[prs]: http://makeapullrequest.com
[github-watch-badge]: https://img.shields.io/github/watchers/hopskipnfall/EmuLinker-K.svg?style=social
[github-watch]: https://github.com/hopskipnfall/EmuLinker-K/watchers
[github-star-badge]: https://img.shields.io/github/stars/hopskipnfall/EmuLinker-K.svg?style=social
[github-star]: https://github.com/hopskipnfall/EmuLinker-K/stargazers
[twitter]: https://twitter.com/intent/tweet?text=https://github.com/hopskipnfall/EmuLinker-K%20%F0%9F%91%8D
[twitter-badge]: https://img.shields.io/twitter/url/https/github.com/hopskipnfall/EmuLinker-K.svg?style=social
[maintained-badge]: https://img.shields.io/badge/maintained-yes-brightgreen
