# NotePilot Project Context

## App

- Name: NotePilot
- Package: `com.smithware.notepilot`
- Repo: `https://github.com/BadBagger/notepilot`
- Purpose: voice-first local notes, tasks, lists, reminders, shopping lists, and idea capture.
- Promise: "Say it. NotePilot organizes it."

## Current MVP

- Kotlin, Jetpack Compose, Room, and DataStore.
- Android platform speech recognition with typed fallback.
- Local rule-based transcript formatter via `TranscriptFormatter` and `LocalRuleBasedFormatter`.
- Placeholder `FutureAiFormatter` for later optional AI work.
- Local reminders with Android notifications.
- Launcher icon uses the supplied NotePilot artwork with transparent outer background.

## Release State

- Latest release `v0.1.1-deadline-reminders` is published at `https://github.com/BadBagger/notepilot/releases/tag/v0.1.1-deadline-reminders` with `NotePilot.apk` and `NotePilot-release-v0.1.1-deadline-reminders.apk`.
- Initial release `v0.1.0-mvp` was published at `https://github.com/BadBagger/notepilot/releases/tag/v0.1.0-mvp`.
- `v0.1.1-deadline-reminders` separates detected deadlines from notification times, defaults reminders to 30 minutes before detected deadlines, recognizes explicit prior offsets, and adds a Room migration for saved reminder timestamps.
- APK certificate SHA-256: `d2210e7f1d9159b5df8088060e22cf76dd60f762e1d7b006cb196fced15a1b5a`.
- DevHub onboarding release is `v2.1.51-notepilot`.
- Release signing uses ignored local `keystore.properties` and a keystore outside the repo under `C:\Users\KyleB\.smithware\signing\com.smithware.notepilot\`.
- Do not commit APKs, keystores, passwords, or local SDK paths.
