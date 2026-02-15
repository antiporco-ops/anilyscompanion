# AGENTS.md — Contract for Agents (Codex / ChatGPT) on `anilyscompanion`

## 0. Scope and Repo
- This repository is `antiporco-ops/anilyscompanion`.
- Tech stack: Android app (Gradle) for the AniLys WatchFaces companion.
- Project root in WSL: `/home/timao/code/anilyscompanion`.
- VS Code runs in WSL (Remote - WSL) and Codex must run commands in WSL.

## 1. Non-negotiables (Security / Privacy)
- NEVER commit:
  - API keys, tokens, client secrets.
  - Keystores (`*.jks`), signing configs.
  - `google-services.json`, `local.properties`.
- Assume this repo is private and “sealed”: no public snippets from this code.
- If any secret is detected:
  - Stop changes immediately.
  - Remove from working tree.
  - Mention in PR description that the secret must be rotated.

## 2. WSL / Workspace Rules
- Work only inside Linux filesystem:
  - `/home/timao/code/anilyscompanion`
- Do NOT assume `/mnt/c/...` for project files (only for Android SDK).
- Use the Gradle wrapper from project root:
  - `./gradlew ...` (never `gradle ...` directly).

## 3. Standard Commands Before Any Pull Request

From `/home/timao/code/anilyscompanion`:

### 3.1 Environment sanity
- `./gradlew --version` (only when tooling/Gradle is changed)
- `./gradlew tasks --all` (optional, for debugging)

### 3.2 Build
- `./gradlew :app:assembleDebug`

### 3.3 Unit tests
- `./gradlew :app:testDebugUnitTest`

### 3.4 Lint
- `./gradlew :app:lintDebug`

### 3.5 Dependencies (when build.gradle[.kts] changes)
- `./gradlew :app:dependencies --configuration debugRuntimeClasspath`

### 3.6 Instrumented tests (optional)
- Only if a device/emulator is connected:
  - `./gradlew :app:connectedDebugAndroidTest`

## 4. Secret Scanning (Manual Checks)
Before first push of a risky change or before releases:

- Quick regex scan:
  - `rg -n "BEGIN PRIVATE KEY|AIzaSy|sk_live|sk_test|secret|token|keystore|storePassword|keyPassword|alias" .`

If additional tools are installed, also:

- `gitleaks git -v .`
- `trufflehog filesystem . --results=verified,unknown`

If any finding appears, DO NOT commit until resolved.

## 5. Change Policy
- Small, focused changes.
- One logical change per commit.
- Avoid mixing big refactors with new features in the same PR.
- Commit message style (recommended):
  - `feat(companion): ...`
  - `fix(companion): ...`
  - `chore(build): ...`

## 6. Branch / PR Workflow

### 6.1 Branches
- Base branch: `main`
- Feature branches:
  - `feat/<short-slug>`
  - `fix/<short-slug>`
  - `chore/<short-slug>`
  - `refactor/<short-slug>`

Example:
- `feat/battery-bridge-prepare`
- `fix/settings-crash`

### 6.2 PR Requirements
Every PR MUST include:

- Confirmation that:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug`
  were run and passed.

- PR description must contain:
  - **Summary**: 2–3 lines about what changed.
  - **Changes**: bullet list.
  - **Test plan**: commands + manual tests.
  - **Risk / rollback**: what might break and how to revert.

### 6.3 Reviews
- Use Codex to review diffs and suggest improvements.
- Use ChatGPT (GitHub Connector) for high-level review:
  - Architecture
  - Performance
  - Wear OS specifics
  - Security (permissions, intents, IPC)

## 7. Future Work: Phone Battery Bridge
- When adding phone-battery-to-watch bridge:
  - Keep logic in a dedicated package (e.g. `batterybridge`).
  - Avoid adding new permissions without explanation in PR.
  - Provide a clear, testable API for battery state exposure.
  - Document any background work/services/listeners in PR description.
