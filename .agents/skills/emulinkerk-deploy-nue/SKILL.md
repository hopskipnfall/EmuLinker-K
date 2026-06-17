---
name: emulinkerk-deploy-nue
description: >-
  Builds, deploys, and restarts the EmuLinker-K server on the 'nue-prod' EC2
  instance safely by checking the server status first.
---

# Deploy EmuLinker-K to nue-prod

## Overview
This skill guides the agent to safely build and deploy a new version of the EmuLinker-K server to the `nue-prod` instance. It ensures that the deployment doesn't interrupt active players by calling the server status skill before restarting.

## Dependencies
- **emulinkerk-server-status**: Used to inspect the remote server log and determine if there are active players or games.

## Quick Start
Run this skill to deploy the current local changes to `nue-prod`.

## Workflow

### 1. Build the local JAR
Run the Gradle clean task and compile the fat JAR:
```bash
./gradlew clean :emulinker:jar -PprodBuild=true
```
Check the version in `emulinker/build.gradle.kts` (look for `version = "<VERSION>"`). The built jar is located at `./emulinker/build/libs/emulinker-k-<VERSION>.jar`.

### 2. Copy the JAR to the remote server
Generate a timestamp in `YYYYMMHH` format:
```bash
TIMESTAMP=$(date +%Y%m%H)
```
Copy the JAR to the remote server using `scp`, appending the timestamp and `-DEV`:
```bash
scp ./emulinker/build/libs/emulinker-k-<VERSION>.jar ec2-user@nue-prod:/home/ec2-user/EmuLinker-K/lib/emulinker-k-<VERSION>-${TIMESTAMP}-DEV.jar
```

### 3. Read and analyze remote logs
Use the `emulinkerk-server-status` skill with:
- **ssh-target**: `ec2-user@nue-prod`
- **log-path**: `/home/ec2-user/EmuLinker-K/emulinker.log`

Determine if the server is safe to restart:
- If the status report shows 0 active games and 0 active users, proceed directly to **Step 5 (Restart Server)**.
- If there are any active games or connected users, proceed to **Step 4 (Warn and Ask User)**.

### 4. Warn and Ask User
If the server is not empty, report the current active games and users to the user.
* **Example**: "Warning: There is 1 active game (ID: 5) and 2 connected users on the server."
* Prompt the user: "Do you want to proceed with restarting the server and disrupting these players?"
* Wait for the user to explicitly reply "yes" before proceeding. If they say "no" or do not confirm, abort the deployment.

### 5. Restart Server
Restart the remote server in a combined SSH command:
```bash
ssh ec2-user@nue-prod "cd /home/ec2-user/EmuLinker-K && ./stop-server.sh && ./start-server.sh --jar ./lib/emulinker-k-<VERSION>-${TIMESTAMP}-DEV.jar"
```
Print the output returned by the script directly to verify successful boot.

## Common Mistakes
- **Restarting blindly**: Forgetting to check the server status first, resulting in booting active players.
- **Incorrect JAR override flag**: Forgetting the `--jar` override when starting the server, which runs the old default production JAR instead of the newly deployed one.
