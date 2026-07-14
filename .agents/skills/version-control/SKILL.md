---
name: version-control
description: Best practices and rules for Git version control, branch management, pull requests, and commit/PR descriptions.
---

# Git & Version Control Guidelines

This skill defines the mandatory workflow rules and formatting requirements for managing source code history, branching, and pull requests in this repository.

---

## 1. Branching & Commit Workflow

1. **Dedicated Branches**: All development work must occur on a dedicated feature branch created off of the `master` branch.
2. **No Direct Commits**: Under no circumstances should commits be pushed directly to the `master` branch, unless explicitly and directly ordered by the user.
3. **Continuous Integration (CI)**: We use GitHub CI on our Pull Requests. A Pull Request must not be merged under any circumstances unless all CI checks pass successfully.
4. **Squash and Merge Only**: We only squash and merge PRs when merging changes into the `master` branch. Other merge strategies (e.g., rebase merge or merge commit) are forbidden.
5. **Pruning Remote Branches**: Immediately after a Pull Request is successfully squashed and merged, the development branch must be deleted from the remote repository.
6. **Rationale**:
   * Critical files (e.g., `release/setup.sh`) have an immediate public-facing impact when merged to `master`. Dedicated branches and CI enforcement prevent accidental deployment of untested or broken changes.
   * Pull Requests act as the primary review gate to keep the `master` branch history clean, atomic, and organized.

---

## 2. Pull Request Format & Descriptions

When creating and editing Pull Requests, follow the Google Engineering Practices for CL Descriptions. The PR description acts as a permanent record of the change.

### A. The Title (First Line)
Treat the PR title as the "first line of a CL description":
* **Summarize What is Done**: Provide a short, focused summary of specifically what is being done by the change. It should be informative enough for future code searchers to skim through history without opening the full PR.
* **Write as an Imperative Command**: Formulate it as a complete sentence written as though it was an order or instruction (e.g., `Delete the widget API` instead of `Deleted the widget API` or `Deletes the widget API`).
* **Do Not Use Prefix Tags**: Never prefix PR description titles with labels like `feat:`, `chore:`, `fix:`, or `docs:`.
* **Separate with Empty Line**: Always leave a blank line between the title and the body of the description.

### B. The Description Body
The description body must communicate the **What** and **Why** of the change in detail:
* **The "What"**: Summarize the major changes so that readers can understand what is being modified without reading the entire diff.
* **The "Why"**: Explain the problem being solved and why this specific approach was chosen. Document the developer's context, including decisions made that are not directly visible in the source code.
* **Prevent Regressions (Chesterton's Fence)**: Explain why code is being introduced or changed so future engineers understand why it exists and don't accidentally break or remove it.
* **Metadata & References**: Include any relevant issue numbers, links to design documents, or test/benchmark results at the bottom of the body.
