---
name: emulinkerk-server-status
description: >-
  Inspects the remote EmuLinker-K server log file via SSH to determine the active
  users, active games, and their current states.
---

# EmuLinker-K Server Status

## Overview
This skill allows the agent to inspect the state of a remote EmuLinker-K server by tailing and analyzing its log file over SSH. It extracts the baseline user and game counts from the last hourly status update and tracks subsequent logs to find the current active user count and active games.

## Quick Start
Provide the SSH connection string (e.g., `ec2-user@nue-prod`) and the absolute path to the log file (e.g., `/home/ec2-user/EmuLinker-K/emulinker.log`) to check the server's current state.

## Workflow

### 1. Fetch Remote Logs
Run the following tail command, substituting `<ssh-target>` and `<log-path>` with the provided inputs:
```bash
ssh <ssh-target> "tail -n 200 <log-path>"
```

### 2. Locate the Baseline Update
Scan backwards in the log lines to find the **last** line containing `[Hourly status update]`. It will look like:
`2026-06-17 11:28:15,796 INFO o.e.k.m.KailleraServer [TaskScheduler] [Hourly status update] Game IDs by status = (no games), number of users = 0`

* Note: If no hourly status update is found within the last 200 lines, run the tail command again with a larger limit (e.g., `1000` or `5000` lines) until the hourly update line is found.

### 3. Establish Baseline State
- **Baseline Games**:
  - If `Game IDs by status = (no games)`, the baseline game set is empty `{}`.
  - Otherwise (e.g. `Game IDs by status = {PLAYING=[10], WAITING=[12]}`), parse the game IDs into an active games set (e.g., `{10: "PLAYING", 12: "WAITING"}`).
- **Baseline Users**:
  - Parse `number of users = <N>`. This is the baseline user count.

### 4. Process Chronological Deltas
Scan all log lines that occurred *after* the baseline hourly status update from oldest to newest:
- **Games**:
  - If a line contains `created: Game[id=<ID> name=<NAME>]: <ROM>`, add the game to the active games set.
  - If a line contains `closed game: Game[id=<ID>` or `game desynched: game closed!`, remove the game `<ID>` from the active games set.
  - If a line contains `started: Game[id=<ID>`, update that game's status to `PLAYING`.
- **Users**:
  - If a line contains `User[id=<ID> name=<NAME>]: login request:`, add the user ID to the list of logged-in users.
  - If a line contains `login denied:` or `quit server:` or `inactivity timeout!` or `banned!` or `disconnected from server` for a user, remove the user from the list of logged-in users.
  - Keep track of the current number of users: `baseline_user_count + post_baseline_logins - post_baseline_disconnects`.

### 5. Report Current Status
Summarize the current state:
- Number of active users (and their names, if any logged in after the baseline).
- Number of active games.
- For each active game: ID, name/ROM, and status (e.g., PLAYING or WAITING).
