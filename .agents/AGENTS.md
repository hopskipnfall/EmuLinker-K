# Agent Guidelines & Repository Documentation

This document contains repository rules and key documentation references for agent development on EmuLinker-K.

---

## 1. Project Context & Description

**EmuLinker-K (ELK)** is a high-performance server that uses the Kaillera protocol to facilitate online multiplayer for retro gaming emulators. Built as a Kotlin rewrite of the classic EmuLinkerSF, it focuses on maximizing performance and stability, patching legacy security/privacy vulnerabilities, and introducing new telemetry and lag reduction systems.

### Core Code Pointers
If you are modifying the server logic, start with these key components:
* [ServerMain.kt](emulinker/src/main/java/org/emulinker/kaillera/pico/ServerMain.kt): Main entry point of the server application.
* [KailleraServer.kt](emulinker/src/main/java/org/emulinker/kaillera/model/KailleraServer.kt): Orchestrates general server state, incoming connections, and active rooms.
* [KailleraUser.kt](emulinker/src/main/java/org/emulinker/kaillera/model/KailleraUser.kt): Represents a single connected client/player and tracks their packet frames and consent status.
* [KailleraGame.kt](emulinker/src/main/java/org/emulinker/kaillera/model/KailleraGame.kt): Manages individual active game sessions, player ready status, and data sync.
* [SurveyManager.kt](emulinker/src/main/java/org/emulinker/kaillera/model/SurveyManager.kt): Manages the lag survey state machine, user consent flow, and telemetry recording.

---

## 2. Developer Workflow Rules

To ensure a safe and organized contribution workflow:

1. **Branch Protection**: **Never** commit directly to the `master` branch under any circumstances, unless you are explicitly ordered to by the user. Always create a feature branch off `master` for development.
2. **GitHub CLI Mutation Restrictions**: Do not perform mutate actions using the GitHub CLI (such as `gh pr create`, `gh pr merge`, `gh pr close`, or `gh release create`) without explicit permission from the user.
3. **Check before Build**: Ensure formatting is clean (`./gradlew spotlessCheck`) and all unit tests pass (`./gradlew test`) before requesting review on your changes.

---

## 3. Key Documentation References

* **Contributing Guidelines:**
  * [CONTRIBUTING.md](CONTRIBUTING.md): Outlines code style guidelines, PR standards, and local development helper commands.
* **Subsystems & Formats:**
  * [N64 Controller Input Encoding](docs/n64_controller_encoding.md): Spec for N64 buttons and joystick axis serialization formats in standard/RAW modes.
  * [Survey Subsystem](docs/survey.md): Details on user eligibility, lag question prompts, and telemetry logging configurations.
* **Agent Operations Skills:**
  * Custom task instructions and operational checklists are stored in the `.agents/skills/` directory. These are automatically discovered and loaded by Antigravity.
