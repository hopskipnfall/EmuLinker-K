---
name: emulinkerk-release
description: >-
  Guides the agent through step-by-step checklists to safely compile, tag, release, and promote versions (dev, beta, prod) of EmuLinker-K.
---

# EmuLinker-K Release Management

## Overview
This skill provides a set of strict, manual checklists for building, publishing, and promoting releases of EmuLinker-K across three channels:
- **Dev**: Unversioned development builds attached as assets on the `0.1.2` release and promoted via the `dev` tag.
- **Beta**: Prerelease versions compiled in prod-mode with prerelease flags, published as GitHub Releases (pre-release), and promoted via the `beta` tag.
- **Prod**: Production versions compiled in prod-mode, published as draft GitHub Releases first, and promoted via the `prod` tag only after explicit confirmation.

---

## Dependencies
None.

---

## Quick Start
To manage a release, locate the corresponding workflow checklist below (Dev, Beta, or Prod) and run each command sequentially. Ensure that all local tests pass and formatting is applied before making any tag changes.

---

## Workflow (Instruction-Only)

> [!IMPORTANT]
> **Pre-Execution Confirmation Rule**: Because release actions occur infrequently (often months apart), the agent **must explicitly write down, enumerate, and present the exact checklist steps and terminal commands** it will run to the user in chat before taking any action. Specifically, before making any code modification, compile, git commit, tag creation, push, or GitHub Release, the agent must present the list of planned actions and wait for the user to explicitly reply and confirm (e.g. "Proceed").

### A. Development Build Workflow (`dev`)
Use this workflow to build and distribute an unversioned development build for internal testing without changing build files or creating official GitHub releases.

1. **Verify Formatting & Linting**:
   ```bash
   ./gradlew spotlessCheck
   ```
2. **Compile the JAR**:
   Compile the jar in production mode but with the `prerelease` flag set to `true`:
   ```bash
   ./gradlew jar -PprodBuild=true -Pprerelease=true
   ```
3. **Rename Target JAR**:
   Rename the output JAR in `emulinker/build/libs/` to `emulinker-k-DEV.jar`.
4. **Upload to First Release Asset**:
   Upload the JAR to the very first release (`0.1.2`) on GitHub, overwriting the existing asset if present:
   ```bash
   gh release upload 0.1.2 emulinker/build/libs/emulinker-k-DEV.jar --clobber
   ```
5. **Update Dev Manifest**:
   Update `release/dev.txt` in the `master` branch:
   ```properties
   tag=dev
   version=DEV
   downloadUrl=https://github.com/hopskipnfall/EmuLinker-K/releases/download/0.1.2/emulinker-k-DEV.jar
   ```
6. **Commit & Push Manifest**:
   ```bash
   git add release/dev.txt
   git commit -m "Update dev release manifest to latest DEV jar"
   git push origin master
   ```
7. **Update the `dev` Tag**:
   Delete and recreate the `dev` tag locally, then force-push it to GitHub:
   ```bash
   git tag -d dev
   git tag dev
   git push origin dev --force
   ```

---

### B. Beta Release Workflow (`beta`)
Use this workflow to release a new public beta test version.

1. **Request Target Version**:
   * **MANDATORY**: Explicitly ask the user in chat what version string they want to release (e.g. `1.2`). Do not guess.
2. **Update Gradle Configuration**:
   Update the `version` variable in `emulinker/build.gradle.kts` to the specified version:
   ```kotlin
   version = "<specified-version>"
   ```
3. **Validate the Code**:
   Run the full linting check and test suite:
   ```bash
   ./gradlew spotlessCheck test
   ```
4. **Compile the JAR**:
   Compile the JAR in production mode with the prerelease flag:
   ```bash
   ./gradlew jar -PprodBuild=true -Pprerelease=true
   ```
5. **Commit the Version Bump**:
   ```bash
   git add emulinker/build.gradle.kts
   git commit -m "Bump version to <specified-version> for beta release"
   git push origin master
   ```
6. **Create the Release Tag**:
   Create a local Git tag named `<specified-version>-beta` (e.g., `1.2-beta`) and push it:
   ```bash
   git tag <specified-version>-beta
   git push origin <specified-version>-beta
   ```
7. **Create the GitHub Pre-release**:
   Create a pre-release on GitHub:
   ```bash
   gh release create <specified-version>-beta --prerelease --title "<specified-version>-beta"
   ```
8. **Upload JAR Asset**:
   Upload the compiled JAR to the GitHub pre-release page:
   ```bash
   gh release upload <specified-version>-beta emulinker/build/libs/emulinker-k-<specified-version>.jar --clobber
   ```
9. **Update Beta Manifest**:
   Update `release/beta.txt` in the `master` branch:
   ```properties
   tag=<specified-version>-beta
   version=<specified-version>
   downloadUrl=https://github.com/hopskipnfall/EmuLinker-K/releases/download/<specified-version>-beta/emulinker-k-<specified-version>.jar
   ```
10. **Commit & Push Manifest**:
    ```bash
    git add release/beta.txt
    git commit -m "Update beta release manifest to <specified-version>-beta"
    git push origin master
    ```
11. **Update the `beta` Tag**:
    Delete and recreate the `beta` tag locally, then force-push it to origin:
    ```bash
    git tag -d beta
    git tag beta
    git push origin beta --force
    ```

---

### C. Production Release Workflow (`prod`)
Use this workflow to release a stable production version.

1. **Request Target Version**:
   * **MANDATORY**: Explicitly ask the user in chat what version string they want to release (e.g., `1.2`). Do not guess.
2. **Update Gradle Configuration**:
   Update the `version` variable in `emulinker/build.gradle.kts` to the specified version:
   ```kotlin
   version = "<specified-version>"
   ```
3. **Validate the Code**:
   Run the full linting check and test suite:
   ```bash
   ./gradlew spotlessCheck test
   ```
4. **Compile the JAR**:
   Compile the JAR in production mode with the prerelease flag turned off:
   ```bash
   ./gradlew jar -PprodBuild=true -Pprerelease=false
   ```
5. **Commit the Version Bump**:
   ```bash
   git add emulinker/build.gradle.kts
   git commit -m "Bump version to <specified-version> for production release"
   git push origin master
   ```
6. **Create the Git Version Tag**:
   Create a local Git tag named `<specified-version>` (e.g., `1.2`) and push it:
   ```bash
   git tag <specified-version>
   git push origin <specified-version>
   ```
7. **Create the GitHub Draft Release**:
   Create a new release on GitHub, marked as a **Draft**:
   ```bash
   gh release create <specified-version> --draft --title "<specified-version>"
   ```
8. **Upload JAR Asset**:
   Upload the compiled JAR to the GitHub draft release page:
   ```bash
   gh release upload <specified-version> emulinker/build/libs/emulinker-k-<specified-version>.jar --clobber
   ```
9. **Update Production Manifest**:
   Update `release/prod.txt` in the `master` branch:
   ```properties
   tag=<specified-version>
   version=<specified-version>
   downloadUrl=https://github.com/hopskipnfall/EmuLinker-K/releases/download/<specified-version>/emulinker-k-<specified-version>.jar
   ```
10. **Commit & Push Manifest**:
    ```bash
    git add release/prod.txt
    git commit -m "Update production release manifest to <specified-version>"
    git push origin master
    ```
11. **Obtain Explicit Confirmation**:
    * **CRITICAL**: You MUST output a bold confirmation question to the user in chat asking for explicit permission to update the `prod` tag (e.g., **"Are you sure you want to update the prod tag to point to this commit?"**). Do not run the next command until the user replies.
12. **Update the `prod` Tag**:
    After confirmation, delete and recreate the `prod` tag locally, then force-push it to origin:
    ```bash
    git tag -d prod
    git tag prod
    git push origin prod --force
    ```

---

## Common Mistakes

1. **Forgetting User Prompts**: Running production tag force-pushes without asking the user for confirmation first.
2. **Missing Prerelease Flag**: Forgetting to pass `-Pprerelease=false` when compiling production versions, or `-Pprerelease=true` when compiling dev/beta versions.
3. **Local Tag Sync Issues**: Forgetting to delete tags locally (`git tag -d <tag>`) before recreating them when shifting channel tags (`dev`, `beta`, `prod`).
