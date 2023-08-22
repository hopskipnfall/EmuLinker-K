# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue or chat with the owners of this repository before making a change.

Please consider joining our Discord server:

[![Discord](https://img.shields.io/badge/Discord-%235865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/MqZEph388c)

## Getting Started

We recommend using [Intellij IDEA](https://www.jetbrains.com/idea/) for development (the free Community Edition works fine). We would like to adopt VSCode as another option but have so far been unsuccessful getting it working (#108).

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

You do not need to have Gradle installed to run these commands. Incidentally, validating this gradle wrapper is a step in our [CI process](./.github/workflows/gradle.yml).

## Best Practices

### Priorities

When making decisions while developing in this repository, keep the following priorities in mind:

1. Respect and protect the privacy of end users and server admins.
2. Don't break compatibility with existing Kaillera clients. 
3. Be as performant as possible on low-end, and especially single-core hardware.
4. Make it easy for non-engineers to stand up, configure, and update ELK servers.
5. Make your code as readable and flexible as possible so other engineers can pick up where you left off.

### Code Style

For Kotlin code we use the [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide). The formatter included in this repository uses [ktfmt](https://github.com/facebook/ktfmt) for automatic formatting (specifically the Google-internal variant), but it doesn't fix all issues so please familiarize yourself with the style guide. 

### Pull Requests

Before sending out a pull request, make sure you run `./gradlew spotlessApply` to format code. We have CI that runs tests and checks for correct formatting on all PRs merging into important long-lived branches.

There is still a lot of legacy code that needs rewriting, but try to keep unrelated changes in your PR to a minimum to make it easier to review PRs and recognize regressions.

For all PRs that may have a significant performance impact, we will want to run load tests.

## TODO

Topics to write about:

- Overview of the protocol
- What is lag?
- Life of a game data packet
- How to encode/decode a Kaillera message
- How to work with charsets
- Viewing metrics for live servers
- Advanced testing: JVM Profiling, load testing
- Instructions for packaging for release and versioning practices
- Steps for urgently notifying server admins of a critical update
- Debugging over the wire with Wireshark
- Changing logs verbosity
